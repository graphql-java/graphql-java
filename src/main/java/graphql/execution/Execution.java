package graphql.execution;


import graphql.GraphQLException;
import graphql.ShouldNotHappenException;
import graphql.introspection.Schema;
import graphql.language.*;
import graphql.schema.*;

import java.util.*;
import java.util.concurrent.*;

import static graphql.introspection.Schema.*;

public class Execution {

    private FieldCollector fieldCollector;
    private Resolver resolver;
    private ExecutorService executorService;

    public Execution(ExecutorService executorService) {
        fieldCollector = new FieldCollector();
        resolver = new Resolver();
        this.executorService = executorService;
    }

    public ExecutionResult execute(GraphQLSchema graphQLSchema, Object root, Document document, String operationName, Map<String, Object> args) {
        ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder(new Resolver());
        ExecutionContext executionContext = executionContextBuilder.build(graphQLSchema, root, document, operationName, args);
        return executeOperation(executionContext, root, executionContext.getOperationDefinition());
    }

    private GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {
        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return graphQLSchema.getMutationType();

        } else if (operationDefinition.getOperation() == OperationDefinition.Operation.QUERY) {
            return graphQLSchema.getQueryType();

        } else {
            throw new GraphQLException();
        }
    }

    private ExecutionResult executeOperation(
            ExecutionContext executionContext,
            Object root,
            OperationDefinition operationDefinition) {
        GraphQLObjectType operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), executionContext.getOperationDefinition());

        Map<String, List<Field>> fields = new LinkedHashMap<>();
        fieldCollector.collectFields(executionContext, operationRootType, operationDefinition.getSelectionSet(), new ArrayList<String>(), fields);


        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            executeFieldsSerially(executionContext, operationRootType, root, fields);
        }
        Object result = executeFields(executionContext, operationRootType, root, fields);
        return new ExecutionResult(result);
    }

    private Map<String, Object> executeFieldsSerially(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields) {
        Map<String, Object> results = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            List<Field> fieldList = fields.get(fieldName);
            Object resolvedResult = resolveField(executionContext, parentType, source, fieldList);
            results.put(fieldName, resolvedResult);
        }
        return results;
    }


    private Map<String, Object> executeFields(final ExecutionContext executionContext, final GraphQLObjectType parentType, final Object source, Map<String, List<Field>> fields) {
        Map<String, Future<Object>> futures = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            final List<Field> fieldList = fields.get(fieldName);
            Callable<Object> resolveField = new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return resolveField(executionContext, parentType, source, fieldList);

                }
            };
            futures.put(fieldName, executorService.submit(resolveField));
        }
        try {
            Map<String, Object> results = new LinkedHashMap<>();
            for (String fieldName : futures.keySet()) {
                results.put(fieldName, futures.get(fieldName).get());
            }
            return results;
        } catch (InterruptedException | ExecutionException e) {
            throw new GraphQLException(e);
        }

    }

    private Object resolveField(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, List<Field> fields) {
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));
        if (fieldDef == null) return null;
        Object resolvedValue;
        Map<String, Object> argumentValues = resolver.getArgumentValues(fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
        DataFetchingEnvironment environment = new DataFetchingEnvironment(
                source,
                argumentValues,
                executionContext.getRoot(),
                fields,
                fieldDef.getType(),
                parentType,
                executionContext.getGraphQLSchema()
        );
        resolvedValue = fieldDef.getDataFetcher().get(environment);


        return completeValue(executionContext, fieldDef.getType(), fields, resolvedValue);
    }

    private Object completeValue(ExecutionContext executionContext, GraphQLType fieldType, List<Field> fields, Object result) {
        if (fieldType instanceof GraphQLNonNull) {
            GraphQLNonNull graphQLNonNull = (GraphQLNonNull) fieldType;
            Object completed = completeValue(executionContext, graphQLNonNull.getWrappedType(), fields, result);
            if (completed == null) throw new GraphQLException("Cannot return null for non-nullable type: " + fields);
            return completed;

        } else if (result == null) {
            return null;
        } else if (fieldType instanceof GraphQLList) {
            return completeValueForList(executionContext, (GraphQLList) fieldType, fields, (List<Object>) result);
        } else if (fieldType instanceof GraphQLScalarType) {
            return completeValueForScalar((GraphQLScalarType) fieldType, result);
        } else if (fieldType instanceof GraphQLEnumType) {
            return completeValueForEnum((GraphQLEnumType) fieldType, result);
        }


        GraphQLObjectType resolvedType;
        if (fieldType instanceof GraphQLInterfaceType) {
            resolvedType = resolveType((GraphQLInterfaceType) fieldType, result);
        } else if (fieldType instanceof GraphQLUnionType) {
            resolvedType = resolveType((GraphQLUnionType) fieldType, result);
        } else {
            resolvedType = (GraphQLObjectType) fieldType;
        }

        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        for (Field field : fields) {
            if (field.getSelectionSet() == null) continue;
            fieldCollector.collectFields(executionContext, resolvedType, field.getSelectionSet(), visitedFragments, subFields);
        }
        return executeFields(executionContext, resolvedType, result, subFields);
    }

    private GraphQLObjectType resolveType(GraphQLInterfaceType graphQLInterfaceType, Object value) {
        GraphQLObjectType result = graphQLInterfaceType.getTypeResolver().getType(value);
        if (result == null) throw new GraphQLException("could not determine type");
        return result;
    }

    private GraphQLObjectType resolveType(GraphQLUnionType graphQLUnionType, Object value) {
        GraphQLObjectType result = graphQLUnionType.getTypeResolver().getType(value);
        if (result == null) throw new GraphQLException("could not determine type");
        return result;
    }


    private Object completeValueForEnum(GraphQLEnumType enumType, Object result) {
        return enumType.getCoercing().coerce(result);
    }

    private Object completeValueForScalar(GraphQLScalarType scalarType, Object result) {
        return scalarType.getCoercing().coerce(result);
    }

    private Object completeValueForList(ExecutionContext executionContext, GraphQLList fieldType, List<Field> fields, List<Object> result) {
        List<Object> completedResults = new ArrayList<>();
        for (Object item : result) {
            completedResults.add(completeValue(executionContext, fieldType.getWrappedType(), fields, item));
        }
        return completedResults;
    }

    private GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLObjectType parentType, Field field) {
        if (schema.getQueryType() == parentType) {
            if (field.getName().equals(SchemaMetaFieldDef.getName())) {
                return SchemaMetaFieldDef;
            }
            if (field.getName().equals(TypeMetaFieldDef.getName())) {
                return TypeMetaFieldDef;
            }
        }
        if (field.getName().equals(TypeNameMetaFieldDef.getName())) {
            return TypeNameMetaFieldDef;
        }

        GraphQLFieldDefinition fieldDefinition = parentType.getFieldDefinition(field.getName());
        if (fieldDefinition == null) throw new GraphQLException("unknown field " + field.getName());
        return fieldDefinition;
    }


}
