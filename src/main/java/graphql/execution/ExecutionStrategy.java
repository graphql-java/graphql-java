package graphql.execution;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.FieldFetchParameters;
import graphql.execution.instrumentation.parameters.FieldParameters;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
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

import static graphql.execution.TypeInfo.newTypeInfo;
import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;

public abstract class ExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStrategy.class);

    protected final ValuesResolver valuesResolver = new ValuesResolver();
    protected final FieldCollector fieldCollector = new FieldCollector();

    public abstract ExecutionResult execute(ExecutionContext executionContext, ExecutionParameters parameters) throws NonNullableFieldWasNullException;

    /**
     * Handle exceptions which occur during data fetching. By default, add all exceptions to the execution context's
     * error's. Subclasses may specify custom handling, e.g. of different behavior with different exception types (e.g.
     * re-throwing certain exceptions).
     *
     * @param executionContext the execution context in play
     * @param fieldDef         the field definition
     * @param argumentValues   the map of arguments
     * @param e                the exception that occurred
     */
    protected void handleDataFetchingException(
            ExecutionContext executionContext,
            GraphQLFieldDefinition fieldDef,
            Map<String, Object> argumentValues,
            Exception e) {
        executionContext.addError(new ExceptionWhileDataFetching(e));
    }


    protected ExecutionResult resolveField(ExecutionContext executionContext, ExecutionParameters parameters, List<Field> fields) {
        GraphQLObjectType type = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), type, fields.get(0));

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());
        DataFetchingEnvironment environment = new DataFetchingEnvironmentImpl(
                parameters.source(),
                argumentValues,
                executionContext.getRoot(),
                fields,
                fieldDef.getType(),
                type,
                executionContext.getGraphQLSchema()
        );

        Instrumentation instrumentation = executionContext.getInstrumentation();

        InstrumentationContext<ExecutionResult> fieldCtx = instrumentation.beginField(new FieldParameters(executionContext, fieldDef));

        InstrumentationContext<Object> fetchCtx = instrumentation.beginFieldFetch(new FieldFetchParameters(executionContext, fieldDef, environment));
        Object resolvedValue = null;
        try {
            resolvedValue = fieldDef.getDataFetcher().get(environment);

            fetchCtx.onEnd(resolvedValue);
        } catch (Exception e) {
            log.warn("Exception while fetching data", e);
            handleDataFetchingException(executionContext, fieldDef, argumentValues, e);
            fetchCtx.onEnd(e);
        }

        TypeInfo fieldType = newTypeInfo()
                .type(fieldDef.getType())
                .parentInfo(parameters.typeInfo())
                .build();


        ExecutionParameters newParameters = ExecutionParameters.newParameters()
                .typeInfo(fieldType)
                .fields(parameters.fields())
                .source(resolvedValue).build();

        ExecutionResult result = completeValue(executionContext, newParameters, fields);

        fieldCtx.onEnd(result);
        return result;
    }

    protected ExecutionResult completeValue(ExecutionContext executionContext, ExecutionParameters parameters, List<Field> fields) {
        TypeInfo typeInfo = parameters.typeInfo();
        Object result = parameters.source();
        GraphQLType fieldType = parameters.typeInfo().type();

        if (result == null) {
            if (typeInfo.typeIsNonNull()) {
                // see http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
                NonNullableFieldWasNullException nonNullException = new NonNullableFieldWasNullException(typeInfo);
                executionContext.addError(nonNullException);
                throw nonNullException;
            }
            return null;
        } else if (fieldType instanceof GraphQLList) {
            return completeValueForList(executionContext, parameters, fields, toIterable(result));
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

        ExecutionParameters newParameters = ExecutionParameters.newParameters()
                .typeInfo(typeInfo.asType(resolvedType))
                .fields(subFields)
                .source(result).build();

        // Calling this from the executionContext to ensure we shift back from mutation strategy to the query strategy.

        return executionContext.getQueryStrategy().execute(executionContext, newParameters);
    }

    private Iterable<Object> toIterable(Object result) {
        if (result.getClass().isArray()) {
            result = Arrays.asList((Object[]) result);
        }
        //noinspection unchecked
        return (Iterable<Object>) result;
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

    protected ExecutionResult completeValueForList(ExecutionContext executionContext, ExecutionParameters parameters, List<Field> fields, Iterable<Object> result) {
        List<Object> completedResults = new ArrayList<>();
        TypeInfo typeInfo = parameters.typeInfo();
        GraphQLList fieldType = typeInfo.castType(GraphQLList.class);
        for (Object item : result) {

            ExecutionParameters newParameters = ExecutionParameters.newParameters()
                    .typeInfo(typeInfo.asType(fieldType.getWrappedType()))
                    .fields(parameters.fields())
                    .source(item).build();

            ExecutionResult completedValue = completeValue(executionContext, newParameters, fields);
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
