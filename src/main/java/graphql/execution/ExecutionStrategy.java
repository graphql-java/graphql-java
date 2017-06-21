package graphql.execution;

import graphql.ExceptionWhileDataFetching;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.PublicSpi;
import graphql.SerializationError;
import graphql.TypeResolutionEnvironment;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.language.Field;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.execution.TypeInfo.newTypeInfo;
import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;

@PublicSpi
public abstract class ExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStrategy.class);

    protected final ValuesResolver valuesResolver = new ValuesResolver();
    protected final FieldCollector fieldCollector = new FieldCollector();

    public abstract ExecutionResult execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException;

    /**
     * Handle exceptions which occur during data fetching. By default, add all exceptions to the execution context's
     * error's. Subclasses may specify custom handling, e.g. of different behavior with different exception types (e.g.
     * re-throwing certain exceptions).
     *
     * @param executionContext the execution context in play
     * @param fieldDef         the field definition
     * @param argumentValues   the map of arguments
     * @param path             the logical path to the field in question
     * @param e                the exception that occurred
     */
    @SuppressWarnings("unused")
    protected void handleDataFetchingException(
            ExecutionContext executionContext,
            GraphQLFieldDefinition fieldDef,
            Map<String, Object> argumentValues,
            ExecutionPath path,
            Exception e) {
        ExceptionWhileDataFetching error = new ExceptionWhileDataFetching(path, e);
        executionContext.addError(error);
        log.warn(error.getMessage(), e);

    }


    protected ExecutionResult resolveField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, List<Field> fields) {
        GraphQLObjectType parentType = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, fields.get(0));

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), fields.get(0).getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, fields);

        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(parameters.source())
                .arguments(argumentValues)
                .fieldDefinition(fieldDef)
                .fields(fields)
                .fieldType(fieldType)
                .parentType(parentType)
                .selectionSet(fieldCollector)
                .build();

        Instrumentation instrumentation = executionContext.getInstrumentation();

        InstrumentationContext<ExecutionResult> fieldCtx = instrumentation.beginField(new InstrumentationFieldParameters(executionContext, fieldDef, environment));

        InstrumentationContext<Object> fetchCtx = instrumentation.beginFieldFetch(new InstrumentationFieldFetchParameters(executionContext, fieldDef, environment));
        Object resolvedValue = null;
        try {
            DataFetcher dataFetcher = fieldDef.getDataFetcher();
            resolvedValue = dataFetcher.get(environment);

            fetchCtx.onEnd(resolvedValue);
        } catch (Exception e) {
            fetchCtx.onEnd(e);
            handleDataFetchingException(executionContext, fieldDef, argumentValues, parameters.path(), e);
        }

        TypeInfo fieldTypeInfo = newTypeInfo()
                .type(fieldType)
                .parentInfo(parameters.typeInfo())
                .build();


        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, fieldTypeInfo);

        ExecutionStrategyParameters newParameters = ExecutionStrategyParameters.newParameters()
                .typeInfo(fieldTypeInfo)
                .fields(parameters.fields())
                .arguments(argumentValues)
                .source(resolvedValue)
                .nonNullFieldValidator(nonNullableFieldValidator)
                .path(parameters.path())
                .build();

        ExecutionResult result = completeValue(executionContext, newParameters, fields);

        fieldCtx.onEnd(result);
        return result;
    }

    protected ExecutionResult completeValue(ExecutionContext executionContext, ExecutionStrategyParameters parameters, List<Field> fields) {
        TypeInfo typeInfo = parameters.typeInfo();
        Object result = parameters.source();
        GraphQLType fieldType = parameters.typeInfo().type();

        if (result == null) {
            return parameters.nonNullFieldValidator().validate(parameters.path(), null);
        } else if (fieldType instanceof GraphQLList) {
            return completeValueForList(executionContext, parameters, fields, toIterable(result));
        } else if (fieldType instanceof GraphQLScalarType) {
            return completeValueForScalar(executionContext, parameters, (GraphQLScalarType) fieldType, result);
        } else if (fieldType instanceof GraphQLEnumType) {
            return completeValueForEnum(executionContext, parameters, (GraphQLEnumType) fieldType, result);
        }


        // when we are here, we have a complex type: Interface, Union or Object
        // and we must go deeper

        GraphQLObjectType resolvedType;
        if (fieldType instanceof GraphQLInterfaceType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLInterfaceType((GraphQLInterfaceType) fieldType)
                    .field(fields.get(0))
                    .value(parameters.source())
                    .argumentValues(parameters.arguments())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForInterface(resolutionParams);

        } else if (fieldType instanceof GraphQLUnionType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLUnionType((GraphQLUnionType) fieldType)
                    .field(fields.get(0))
                    .value(parameters.source())
                    .argumentValues(parameters.arguments())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForUnion(resolutionParams);
        } else {
            resolvedType = (GraphQLObjectType) fieldType;
        }

        FieldCollectorParameters collectorParameters = newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(resolvedType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();

        Map<String, List<Field>> subFields = fieldCollector.collectFields(collectorParameters, fields);

        TypeInfo newTypeInfo = typeInfo.asType(resolvedType);
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, newTypeInfo);

        ExecutionStrategyParameters newParameters = ExecutionStrategyParameters.newParameters()
                .typeInfo(newTypeInfo)
                .fields(subFields)
                .nonNullFieldValidator(nonNullableFieldValidator)
                .path(parameters.path())
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

    protected GraphQLObjectType resolveTypeForInterface(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLInterfaceType(), params.getSchema());
        GraphQLObjectType result = params.getGraphQLInterfaceType().getTypeResolver().getType(env);
        if (result == null) {
            throw new GraphQLException("Could not determine the exact type of " + params.getGraphQLInterfaceType().getName());
        }
        return result;
    }

    protected GraphQLObjectType resolveTypeForUnion(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLUnionType(), params.getSchema());
        GraphQLObjectType result = params.getGraphQLUnionType().getTypeResolver().getType(env);
        if (result == null) {
            throw new GraphQLException("Could not determine the exact type of " + params.getGraphQLUnionType().getName());
        }
        return result;
    }

    protected ExecutionResult completeValueForEnum(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLEnumType enumType, Object result) {
        Object serialized;
        try {
            serialized = enumType.getCoercing().serialize(result);
        } catch (CoercingSerializeException e) {
            serialized = handleCoercionProblem(executionContext, parameters, e);
        }
        serialized = parameters.nonNullFieldValidator().validate(parameters.path(), serialized);
        return new ExecutionResultImpl(serialized, null);
    }

    protected ExecutionResult completeValueForScalar(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLScalarType scalarType, Object result) {
        Object serialized;
        try {
            serialized = scalarType.getCoercing().serialize(result);
        } catch (CoercingSerializeException e) {
            serialized = handleCoercionProblem(executionContext, parameters, e);
        }

        // TODO: fix that: this should not be handled here
        //6.6.1 http://facebook.github.io/graphql/#sec-Field-entries
        if (serialized instanceof Double && ((Double) serialized).isNaN()) {
            serialized = null;
        }
        serialized = parameters.nonNullFieldValidator().validate(parameters.path(), serialized);
        return new ExecutionResultImpl(serialized, null);
    }

    private Object handleCoercionProblem(ExecutionContext context, ExecutionStrategyParameters parameters, CoercingSerializeException e) {
        SerializationError error = new SerializationError(parameters.path(), e);
        log.warn(error.getMessage(), e);
        context.addError(error);
        return null;
    }

    protected ExecutionResult completeValueForList(ExecutionContext executionContext, ExecutionStrategyParameters parameters, List<Field> fields, Iterable<Object> result) {
        List<Object> completedResults = new ArrayList<>();
        TypeInfo typeInfo = parameters.typeInfo();
        GraphQLList fieldType = typeInfo.castType(GraphQLList.class);
        int idx = 0;
        for (Object item : result) {

            TypeInfo wrappedTypeInfo = typeInfo.asType(fieldType.getWrappedType());
            NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, wrappedTypeInfo);

            ExecutionPath indexedPath = parameters.path().segment(idx);

            ExecutionStrategyParameters newParameters = ExecutionStrategyParameters.newParameters()
                    .typeInfo(wrappedTypeInfo)
                    .fields(parameters.fields())
                    .nonNullFieldValidator(nonNullableFieldValidator)
                    .path(indexedPath)
                    .source(item).build();

            ExecutionResult completedValue = completeValue(executionContext, newParameters, fields);
            completedResults.add(completedValue != null ? completedValue.getData() : null);
            idx++;
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
}
