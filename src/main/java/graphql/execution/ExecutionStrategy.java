package graphql.execution;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;

public abstract class ExecutionStrategy {
    private static final Logger log = LoggerFactory.getLogger(ExecutionStrategy.class);

    protected final ValuesResolver valuesResolver = new ValuesResolver();
    protected final FieldCollector fieldCollector = new FieldCollector();

    public abstract ExecutionResult execute(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields);

    protected ExecutionResult resolveField(final ExecutionContext executionContext, final GraphQLObjectType parentType, final Object source, final List<Field> fields) {
        final ExecutionConstraints executionConstraints = executionContext.getExecutionConstraints();
        return executionConstraints.callTrackingDepth(new Callable<ExecutionResult>() {
            @Override
            public ExecutionResult call() throws Exception {
                return resolveFieldImpl(executionConstraints, executionContext, parentType, source, fields);
            }
        });
    }

    private ExecutionResult resolveFieldImpl(ExecutionConstraints executionConstraints, ExecutionContext executionContext, GraphQLObjectType parentType, Object source, List<Field> fields) {
        //
        // we return null if the max depth has been exceeded to protect against large or malicious queries
        if (executionConstraints.hasExceededMaxDepthConstraint()) {
            return null;
        }

        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
        DataFetchingEnvironment environment = new DataFetchingEnvironmentImpl(
                source,
                argumentValues,
                executionContext.getRoot(),
                fields,
                fieldDef.getType(),
                parentType,
                executionContext.getGraphQLSchema()
        );

        Object resolvedValue = null;
        try {
            resolvedValue = fieldDef.getDataFetcher().get(environment);
        } catch (Exception e) {
            log.warn("Exception while fetching data", e);
            executionContext.addError(new ExceptionWhileDataFetching(e));
        }

        return completeValue(executionContext, fieldDef.getType(), fields, resolvedValue);
    }

    protected ExecutionResult completeValue(ExecutionContext executionContext, GraphQLType fieldType, List<Field> fields, Object result) {
        if (fieldType instanceof GraphQLNonNull) {
            GraphQLNonNull graphQLNonNull = (GraphQLNonNull) fieldType;
            ExecutionResult completed = completeValue(executionContext, graphQLNonNull.getWrappedType(), fields, result);
            if (completed == null) {
                throw new GraphQLException("Cannot return null for non-nullable type: " + fields);
            }
            return completed;

        } else if (result == null) {
            return null;
        } else if (fieldType instanceof GraphQLList) {
            return completeValueForList(executionContext, (GraphQLList) fieldType, fields, result);
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

        Map<String, List<Field>> subFields = new LinkedHashMap<String, List<Field>>();
        List<String> visitedFragments = new ArrayList<String>();
        for (Field field : fields) {
            if (field.getSelectionSet() == null) continue;
            fieldCollector.collectFields(executionContext, resolvedType, field.getSelectionSet(), visitedFragments, subFields);
        }

        // Calling this from the executionContext to ensure we shift back from mutation strategy to the query strategy.

        return executionContext.getQueryStrategy().execute(executionContext, resolvedType, result, subFields);
    }

    private ExecutionResult completeValueForList(ExecutionContext executionContext, GraphQLList fieldType, List<Field> fields, Object result) {
        if (result.getClass().isArray()) {
            result = Arrays.asList((Object[]) result);
        }

        //noinspection unchecked
        return completeValueForList(executionContext, fieldType, fields, (Iterable<Object>) result);
    }

    protected GraphQLObjectType resolveType(GraphQLInterfaceType graphQLInterfaceType, Object value) {
        GraphQLObjectType result = graphQLInterfaceType.getTypeResolver().getType(value);
        if (result == null) {
            throw new GraphQLException("could not determine type");
        }
        return result;
    }

    protected GraphQLObjectType resolveType(GraphQLUnionType graphQLUnionType, Object value) {
        GraphQLObjectType result = graphQLUnionType.getTypeResolver().getType(value);
        if (result == null) {
            throw new GraphQLException("could not determine type");
        }
        return result;
    }


    protected ExecutionResult completeValueForEnum(GraphQLEnumType enumType, Object result) {
        return new ExecutionResultImpl(enumType.getCoercing().serialize(result), null);
    }

    protected ExecutionResult completeValueForScalar(GraphQLScalarType scalarType, Object result) {
        Object serialized = scalarType.getCoercing().serialize(result);
        //6.6.1 http://facebook.github.io/graphql/#sec-Field-entries
        if (serialized instanceof Double && ((Double) serialized).isNaN()) {
            serialized = null;
        }
        return new ExecutionResultImpl(serialized, null);
    }

    protected ExecutionResult completeValueForList(ExecutionContext executionContext, GraphQLList fieldType, List<Field> fields, Iterable<Object> result) {
        List<Object> completedResults = new ArrayList<Object>();
        for (Object item : result) {
            ExecutionResult completedValue = completeValue(executionContext, fieldType.getWrappedType(), fields, item);
            completedResults.add(completedValue != null ? completedValue.getData() : null);
        }
        return new ExecutionResultImpl(completedResults, null);
    }

    protected GraphQLFieldDefinition getFieldDef(GraphQLSchema schema, GraphQLObjectType parentType, Field field) {
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
        if (fieldDefinition == null) {
            throw new GraphQLException("unknown field " + field.getName());
        }
        return fieldDefinition;
    }


}
