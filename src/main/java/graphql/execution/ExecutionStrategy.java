package graphql.execution;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.TypeResolutionEnvironment;
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

import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;

public abstract class ExecutionStrategy {
    private static final Logger log = LoggerFactory.getLogger(ExecutionStrategy.class);

    protected final ValuesResolver valuesResolver = new ValuesResolver();
    protected final FieldCollector fieldCollector = new FieldCollector();

    public abstract ExecutionResult execute(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, Map<String, List<Field>> fields);

    protected ExecutionResult resolveField(ExecutionContext executionContext, GraphQLObjectType parentType, Object source, List<Field> fields) {
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

        Instrumentation instrumentation = executionContext.getInstrumentation();

        InstrumentationContext<ExecutionResult> fieldCtx = instrumentation.beginField(new FieldParameters(executionContext, fieldDef));

        InstrumentationContext<Object> fetchCtx = instrumentation.beginFieldFetch(new FieldFetchParameters(executionContext, fieldDef, environment));
        Object resolvedValue = null;
        try {
            resolvedValue = fieldDef.getDataFetcher().get(environment);

            fetchCtx.onEnd(resolvedValue);
        } catch (Exception e) {
            log.warn("Exception while fetching data", e);
            executionContext.addError(new ExceptionWhileDataFetching(e));

            fetchCtx.onEnd(e);
        }

        ExecutionResult result = completeValue(createCompletionParams(executionContext, fieldDef.getType(), fields, resolvedValue, argumentValues));

        fieldCtx.onEnd(result);
        return result;
    }

    /**
     * @deprecated Use {@link #completeValue(ValueCompletionParameters)} instead
     */
    @Deprecated
    protected ExecutionResult completeValue(ExecutionContext executionContext, GraphQLType fieldType, List<Field> fields, Object result) {
        return completeValue(createCompletionParams(executionContext, fieldType, fields, result, null));
    }

    protected ExecutionResult completeValue(ValueCompletionParameters params) {
        if (params.getFieldType() instanceof GraphQLNonNull) {
            ValueCompletionParameters unwrapped = createCompletionParams(params.getExecutionContext(), params.<GraphQLNonNull>getFieldType().getWrappedType(),
                    params.getFields(), params.getResult(), params.getArgumentValues()
            );
            ExecutionResult completed = completeValue(unwrapped);
            if (completed == null) {
                throw new GraphQLException("Cannot return null for non-nullable type: " + params.getFields());
            }
            return completed;

        } else if (params.getResult() == null) {
            return null;
        } else if (params.getFieldType() instanceof GraphQLList) {
            return completeValueForListOrArray(params);
        } else if (params.getFieldType() instanceof GraphQLScalarType) {
            return completeValueForScalar(params.getFieldType(), params.getResult());
        } else if (params.getFieldType() instanceof GraphQLEnumType) {
            return completeValueForEnum(params.getFieldType(), params.getResult());
        }


        GraphQLObjectType resolvedType;
        if (params.getFieldType() instanceof GraphQLInterfaceType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLInterfaceType(params.getFieldType())
                    .field(params.getFields().get(0))
                    .value(params.getResult())
                    .argumentValues(params.getArgumentValues())
                    .schema(params.getExecutionContext().getGraphQLSchema()).build();
            resolvedType = resolveTypeForInterface(resolutionParams);
        } else if (params.getFieldType() instanceof GraphQLUnionType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLUnionType(params.getFieldType())
                    .field(params.getFields().get(0))
                    .value(params.getResult())
                    .argumentValues(params.getArgumentValues())
                    .schema(params.getExecutionContext().getGraphQLSchema()).build();
            resolvedType = resolveTypeForUnion(resolutionParams);
        } else {
            resolvedType = params.getFieldType();
        }

        Map<String, List<Field>> subFields = new LinkedHashMap<>();
        List<String> visitedFragments = new ArrayList<>();
        for (Field field : params.getFields()) {
            if (field.getSelectionSet() == null) continue;
            fieldCollector.collectFields(params.getExecutionContext(), resolvedType, field.getSelectionSet(), visitedFragments, subFields);
        }

        // Calling this from the executionContext to ensure we shift back from mutation strategy to the query strategy.

        return params.getExecutionContext().getQueryStrategy().execute(params.getExecutionContext(), resolvedType, params.getResult(), subFields);
    }

    private ExecutionResult completeValueForListOrArray(ValueCompletionParameters params) {
        if (params.getResult().getClass().isArray()) {
            List<Object> result = Arrays.asList((Object[]) params.getResult());
            params = createCompletionParams(params.getExecutionContext(), params.getFieldType(), params.getFields(), result, params.getArgumentValues());
        }

        //noinspection unchecked
        return completeValueForList(params);
    }

    /**
     * @deprecated Use {@link #resolveTypeForInterface(TypeResolutionParameters)}
     */
    @Deprecated
    protected GraphQLObjectType resolveType(GraphQLInterfaceType graphQLInterfaceType, Object value) {
        return resolveTypeForInterface(TypeResolutionParameters.newParameters().graphQLInterfaceType(graphQLInterfaceType).value(value).build());
    }

    protected GraphQLObjectType resolveTypeForInterface(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLInterfaceType(), params.getSchema());
        GraphQLObjectType result = params.getGraphQLInterfaceType().getTypeResolver().getType(env);
        if (result == null) {
            throw new GraphQLException("Could not determine the exact type of " + params.getGraphQLInterfaceType().getName());
        }
        return result;
    }

    /**
     * @deprecated Use {@link #resolveTypeForUnion(TypeResolutionParameters)}
     */
    @Deprecated
    protected GraphQLObjectType resolveType(GraphQLUnionType graphQLUnionType, Object value) {
        return resolveTypeForUnion(TypeResolutionParameters.newParameters().graphQLUnionType(graphQLUnionType).value(value).build());
    }

    protected GraphQLObjectType resolveTypeForUnion(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLUnionType(), params.getSchema());
        GraphQLObjectType result = params.getGraphQLUnionType().getTypeResolver().getType(env);
        if (result == null) {
            throw new GraphQLException("Could not determine the exact type of " + params.getGraphQLUnionType().getName());
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

    /**
     * @deprecated Use {@link #completeValueForList(ValueCompletionParameters)}
     */
    @Deprecated
    protected ExecutionResult completeValueForList(ExecutionContext executionContext, GraphQLList fieldType, List<Field> fields, Iterable<Object> result) {
        return completeValueForList(createCompletionParams(executionContext, fieldType, fields, result, null));
    }

    protected ExecutionResult completeValueForList(ValueCompletionParameters params) {
        List<Object> completedResults = new ArrayList<>();
        for (Object item : params.<Iterable<Object>>getResult()) {
            ValueCompletionParameters unwrapped = createCompletionParams(params.getExecutionContext(), params.<GraphQLList>getFieldType().getWrappedType(),
                    params.getFields(), item, params.getArgumentValues()
            );
            ExecutionResult completedValue = completeValue(unwrapped);
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
            throw new GraphQLException("Unknown field " + field.getName());
        }
        return fieldDefinition;
    }

    private ValueCompletionParameters createCompletionParams(ExecutionContext executionContext, GraphQLType fieldType,
                                                             List<Field> fields, Object result, Map<String, Object> argumentValues) {
        return ValueCompletionParameters.newParameters()
                .executionContext(executionContext)
                .fieldType(fieldType)
                .fields(fields)
                .result(result)
                .argumentValues(argumentValues)
                .build();
    }
}
