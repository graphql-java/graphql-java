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
import java.util.concurrent.CompletableFuture;

import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.execution.TypeInfo.newTypeInfo;
import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;

/**
 * An execution strategy is give a list of fields from the graphql query to execute and find values for using a recursive strategy.
 *
 * <pre>
 *     query {
 *          friends {
 *              id
 *              name
 *              friends {
 *                  id
 *                  name
 *              }
 *          }
 *          enemies {
 *              id
 *              name
 *              allies {
 *                  id
 *                  name
 *              }
 *          }
 *     }
 *
 * </pre>
 *
 * Given the graphql query above, an execution strategy will be called for the top level fields 'friends' and 'enemies' and it will be asked to find an object
 * to describe them.  Because they are both complex object types, it needs to descend down that query and start fetching and completing
 * fields such as 'id','name' and other complex fields such as 'friends' and 'allies', by recursively calling to itself to execute these lower
 * field layers
 * <p>
 * The execution of a field has two phases, first a raw object must be fetched for a field via a {@link DataFetcher} which
 * is defined on the {@link GraphQLFieldDefinition}.  This object must then be 'completed' into a suitable value, either as a scalar/enum type via
 * coercion or if its a complex object type by recursively calling the execution strategy for the lower level fields.
 * <p>
 * The first phase (data fetching) is handled by the method {@link #fetchField(ExecutionContext, ExecutionStrategyParameters)}
 * <p>
 * The second phase (value completion) is handled by the methods {@link #completeField(ExecutionContext, ExecutionStrategyParameters, Object)}
 * and the other "completeXXX" methods.
 * <p>
 * The order of fields fetching and completion is up to the execution strategy. As the graphql specification
 * <a href="http://facebook.github.io/graphql/#sec-Normal-and-Serial-Execution">http://facebook.github.io/graphql/#sec-Normal-and-Serial-Execution</a> says:
 * <blockquote>
 * Normally the executor can execute the entries in a grouped field set in whatever order it chooses (often in parallel). Because
 * the resolution of fields other than top‐level mutation fields must always be side effect‐free and idempotent, the
 * execution order must not affect the result, and hence the server has the freedom to execute the
 * field entries in whatever order it deems optimal.
 * </blockquote>
 * <p>
 * So in the case above you could execute the fields depth first ('friends' and its sub fields then do 'enemies' and its sub fields or it
 * could do breadth first ('fiends' and 'enemies' data fetch first and then all the sub fields) or in parallel via asynchronous
 * facilities like {@link CompletableFuture}s.
 * <p>
 * {@link #execute(ExecutionContext, ExecutionStrategyParameters)} is the entry point of the execution strategy.
 */
@PublicSpi
public abstract class ExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(ExecutionStrategy.class);

    protected final ValuesResolver valuesResolver = new ValuesResolver();
    protected final FieldCollector fieldCollector = new FieldCollector();

    protected final DataFetcherExceptionHandler dataFetcherExceptionHandler;

    protected ExecutionStrategy() {
        //
        // a legacy handler for classes that have decided to override #handleDataFetchingException
        //
        //noinspection deprecation
        dataFetcherExceptionHandler = params -> handleDataFetchingException(
                params.getExecutionContext(),
                params.getFieldDefinition(),
                params.getArgumentValues(),
                params.getPath(),
                params.getException()
        );
    }

    /**
     * The consumers of the execution strategy can pass in a {@link DataFetcherExceptionHandler} to better
     * decide what do when a data fetching error happens
     *
     * @param dataFetcherExceptionHandler the callback invoked if an exception happens during data fetching
     */
    protected ExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        this.dataFetcherExceptionHandler = dataFetcherExceptionHandler;
    }

    /**
     * This is the entry point to an execution strategy.  It will be passed the fields to execute and get values for.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return an {@link ExecutionResult}
     *
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
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
     *
     * @deprecated implement the new {@link DataFetcherExceptionHandler} interface and pass it in as a parameter to the strategy
     */
    @SuppressWarnings({"unused", "DeprecatedIsStillUsed"})
    @Deprecated
    protected void handleDataFetchingException(
            ExecutionContext executionContext,
            GraphQLFieldDefinition fieldDef,
            Map<String, Object> argumentValues,
            ExecutionPath path,
            Exception e) {

        // for legacy reasons we do what we always did.  Later this will be removed
        ExceptionWhileDataFetching error = new ExceptionWhileDataFetching(path, e, null);
        executionContext.addError(error);
        log.warn(error.getMessage(), e);
    }

    /**
     * Called to fetch a value for a field and resolve it further in terms of the graphql query.  This will call
     * #fetchField followed by #completeField and the completed {@link ExecutionResult} is returned.
     *
     * An execution strategy can iterate the fields to be executed and call this method for each one
     *
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @return an {@link ExecutionResult}
     *
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected ExecutionResult resolveField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext, parameters, parameters.field().get(0));

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationContext<ExecutionResult> fieldCtx = instrumentation.beginField(new InstrumentationFieldParameters(executionContext, fieldDef));

        Object fetchedValue = fetchField(executionContext, parameters);

        ExecutionResult result = completeField(executionContext, parameters, fetchedValue);

        fieldCtx.onEnd(result);
        return result;
    }

    /**
     * Called to fetch a value for a field from the {@link DataFetcher} associated with the field
     * {@link GraphQLFieldDefinition}.
     *
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @return an fetched object
     *
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected Object fetchField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        Field field = parameters.field().get(0);
        GraphQLObjectType parentType = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field);

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), field.getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, parameters.field());

        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(parameters.source())
                .arguments(argumentValues)
                .fieldDefinition(fieldDef)
                .fields(parameters.field())
                .fieldType(fieldType)
                .parentType(parentType)
                .selectionSet(fieldCollector)
                .build();

        Instrumentation instrumentation = executionContext.getInstrumentation();

        InstrumentationContext<Object> fetchCtx = instrumentation.beginFieldFetch(new InstrumentationFieldFetchParameters(executionContext, fieldDef, environment));
        Object fetchedValue = null;
        try {
            DataFetcher dataFetcher = fieldDef.getDataFetcher();
            fetchedValue = dataFetcher.get(environment);

            fetchCtx.onEnd(fetchedValue);
        } catch (Exception e) {
            fetchCtx.onEnd(e);

            DataFetcherExceptionHandlerParameters handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                    .executionContext(executionContext)
                    .dataFetchingEnvironment(environment)
                    .argumentValues(argumentValues)
                    .field(field)
                    .fieldDefinition(fieldDef)
                    .path(parameters.path())
                    .exception(e)
                    .build();

            dataFetcherExceptionHandler.accept(handlerParameters);
        }
        return fetchedValue;
    }

    /**
     * Called to complete a field based on the type of the field.
     *
     * If the field is a scalar type, then it will be coerced  and returned.  However if the field type is an complex object type, then
     * the execution strategy will be called recursively again to execute the fields of that type before returning.
     *
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param fetchedValue     the fetched raw value
     *
     * @return an {@link ExecutionResult}
     *
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected ExecutionResult completeField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Object fetchedValue) {
        Field field = parameters.field().get(0);
        GraphQLObjectType parentType = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field);

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), field.getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        TypeInfo fieldTypeInfo = newTypeInfo()
                .type(fieldType)
                .parentInfo(parameters.typeInfo())
                .build();

        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, fieldTypeInfo);

        ExecutionStrategyParameters newParameters = ExecutionStrategyParameters.newParameters()
                .typeInfo(fieldTypeInfo)
                .field(parameters.field())
                .fields(parameters.fields())
                .arguments(argumentValues)
                .source(fetchedValue)
                .nonNullFieldValidator(nonNullableFieldValidator)
                .path(parameters.path())
                .build();

        return completeValue(executionContext, newParameters);
    }


    /**
     * Called to complete a value for a field based on the type of the field.
     *
     * If the field is a scalar type, then it will be coerced  and returned.  However if the field type is an complex object type, then
     * the execution strategy will be called recursively again to execute the fields of that type before returning.
     *
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return an {@link ExecutionResult}
     *
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected ExecutionResult completeValue(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        TypeInfo typeInfo = parameters.typeInfo();
        Object result = parameters.source();
        GraphQLType fieldType = parameters.typeInfo().type();

        if (result == null) {
            return parameters.nonNullFieldValidator().validate(parameters.path(), null);
        } else if (fieldType instanceof GraphQLList) {
            return completeValueForList(executionContext, parameters, toIterable(result));
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
                    .field(parameters.field().get(0))
                    .value(parameters.source())
                    .argumentValues(parameters.arguments())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForInterface(resolutionParams);

        } else if (fieldType instanceof GraphQLUnionType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLUnionType((GraphQLUnionType) fieldType)
                    .field(parameters.field().get(0))
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

        Map<String, List<Field>> subFields = fieldCollector.collectFields(collectorParameters, parameters.field());

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

    /**
     * Called to resolve a {@link GraphQLInterfaceType} into a specific {@link GraphQLObjectType} so the object can be executed in terms of that type
     *
     * @param params the params needed for type resolution
     *
     * @return a {@link GraphQLObjectType}
     */
    protected GraphQLObjectType resolveTypeForInterface(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLInterfaceType(), params.getSchema());
        GraphQLObjectType result = params.getGraphQLInterfaceType().getTypeResolver().getType(env);
        if (result == null) {
            throw new GraphQLException("Could not determine the exact type of " + params.getGraphQLInterfaceType().getName());
        }
        return result;
    }

    /**
     * Called to resolve a {@link GraphQLUnionType} into a specific {@link GraphQLObjectType} so the object can be executed in terms of that type
     *
     * @param params the params needed for type resolution
     *
     * @return a {@link GraphQLObjectType}
     */
    protected GraphQLObjectType resolveTypeForUnion(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLUnionType(), params.getSchema());
        GraphQLObjectType result = params.getGraphQLUnionType().getTypeResolver().getType(env);
        if (result == null) {
            throw new GraphQLException("Could not determine the exact type of " + params.getGraphQLUnionType().getName());
        }
        return result;
    }

    /**
     * Called to turn an object into a enum value according to the {@link GraphQLEnumType} by asking that enum type to coerce the object into a valid value
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param enumType         the type of the enum
     * @param result           the result to be coerced
     *
     * @return an {@link ExecutionResult}
     */
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

    /**
     * Called to turn an object into a scale value according to the {@link GraphQLScalarType} by asking that scalar type to coerce the object
     * into a valid value
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param scalarType       the type of the scalar
     * @param result           the result to be coerced
     *
     * @return an {@link ExecutionResult}
     */
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

    /**
     * Called to complete a list of value for a field based on a list type.  This iterates the values and calls
     * {@link #completeValue(ExecutionContext, ExecutionStrategyParameters)} for each value.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param iterableValues   the values to complete
     *
     * @return an {@link ExecutionResult}
     */
    protected ExecutionResult completeValueForList(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Iterable<Object> iterableValues) {
        List<Object> completedResults = new ArrayList<>();
        TypeInfo typeInfo = parameters.typeInfo();
        GraphQLList fieldType = typeInfo.castType(GraphQLList.class);
        int idx = 0;
        for (Object item : iterableValues) {

            TypeInfo wrappedTypeInfo = typeInfo.asType(fieldType.getWrappedType());
            NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, wrappedTypeInfo);

            ExecutionPath indexedPath = parameters.path().segment(idx);

            ExecutionStrategyParameters newParameters = ExecutionStrategyParameters.newParameters()
                    .typeInfo(wrappedTypeInfo)
                    .fields(parameters.fields())
                    .nonNullFieldValidator(nonNullableFieldValidator)
                    .path(indexedPath)
                    .field(parameters.field())
                    .source(item).build();

            ExecutionResult completedValue = completeValue(executionContext, newParameters);
            completedResults.add(completedValue != null ? completedValue.getData() : null);
            idx++;
        }
        return new ExecutionResultImpl(completedResults, null);
    }

    /**
     * Called to discover the field definition give the current parameters and the AST {@link Field}
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param field            the field to find the definition of
     *
     * @return a {@link GraphQLFieldDefinition}
     */
    protected GraphQLFieldDefinition getFieldDef(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Field field) {
        GraphQLObjectType parentType = parameters.typeInfo().castType(GraphQLObjectType.class);
        return getFieldDef(executionContext.getGraphQLSchema(), parentType, field);
    }

    /**
     * Called to discover the field definition give the current parameters and the AST {@link Field}
     *
     * @param schema     the schema in play
     * @param parentType the parent type of the field
     * @param field      the field to find the definition of
     *
     * @return a {@link GraphQLFieldDefinition}
     */
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

    /**
     * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability),
     *
     * If a non nullable child field type actually resolves to a null value and the parent type is nullable
     * then the parent must in fact become null
     * so we use exceptions to indicate this special case.  However if the parent is in fact a non nullable type
     * itself then we need to bubble that upwards again until we get to the root in which case the result
     * is meant to be null.
     *
     * @param e this indicates that a null value was returned for a non null field, which needs to cause the parent field
     *          to become null OR continue on as an exception
     *
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected void assertNonNullFieldPrecondition(NonNullableFieldWasNullException e) throws NonNullableFieldWasNullException {
        TypeInfo typeInfo = e.getTypeInfo();
        if (typeInfo.hasParentType() && typeInfo.parentTypeInfo().typeIsNonNull()) {
            throw e;
        }
    }
}
