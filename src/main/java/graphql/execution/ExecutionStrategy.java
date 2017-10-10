package graphql.execution;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

import static graphql.execution.ExecutionTypeInfo.newTypeInfo;
import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.introspection.Introspection.SchemaMetaFieldDef;
import static graphql.introspection.Introspection.TypeMetaFieldDef;
import static graphql.introspection.Introspection.TypeNameMetaFieldDef;
import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;

/**
 * An execution strategy is give a list of fields from the graphql query to execute and find values for using a recursive strategy.
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
 * <p>
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
 * the resolution of fields other than top-level mutation fields must always be side effect-free and idempotent, the
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

    /**
     * The default execution strategy constructor uses the {@link SimpleDataFetcherExceptionHandler}
     * for data fetching errors.
     */
    protected ExecutionStrategy() {
        dataFetcherExceptionHandler = new SimpleDataFetcherExceptionHandler();
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
    public abstract CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException;

    /**
     * Called to fetch a value for a field and resolve it further in terms of the graphql query.  This will call
     * #fetchField followed by #completeField and the completed {@link ExecutionResult} is returned.
     * <p>
     * An execution strategy can iterate the fields to be executed and call this method for each one
     * <p>
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
    protected CompletableFuture<ExecutionResult> resolveField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext, parameters, parameters.field().get(0));

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationContext<ExecutionResult> fieldCtx = instrumentation.beginField(
                new InstrumentationFieldParameters(executionContext, fieldDef, fieldTypeInfo(parameters, fieldDef))
        );

        CompletableFuture<ExecutionResult> result = fetchField(executionContext, parameters)
                .thenCompose((fetchedValue) ->
                        completeField(executionContext, parameters, fetchedValue));

        result.whenComplete(fieldCtx::onEnd);
        return result;
    }

    /**
     * Called to fetch a value for a field from the {@link DataFetcher} associated with the field
     * {@link GraphQLFieldDefinition}.
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return a fetched object
     *
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected CompletableFuture<Object> fetchField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        Field field = parameters.field().get(0);
        GraphQLObjectType parentType = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field);

        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), field.getArguments(), executionContext.getVariables());

        GraphQLOutputType fieldType = fieldDef.getType();
        DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext, fieldType, parameters.field());
        ExecutionTypeInfo fieldTypeInfo = fieldTypeInfo(parameters, fieldDef);

        DataFetchingEnvironment environment = newDataFetchingEnvironment(executionContext)
                .source(parameters.source())
                .arguments(argumentValues)
                .fieldDefinition(fieldDef)
                .fields(parameters.field())
                .fieldType(fieldType)
                .fieldTypeInfo(fieldTypeInfo)
                .parentType(parentType)
                .selectionSet(fieldCollector)
                .build();

        Instrumentation instrumentation = executionContext.getInstrumentation();

        InstrumentationFieldFetchParameters instrumentationFieldFetchParams = new InstrumentationFieldFetchParameters(executionContext, fieldDef, environment);
        InstrumentationContext<Object> fetchCtx = instrumentation.beginFieldFetch(instrumentationFieldFetchParams);
        CompletableFuture<?> fetchedValue;
        DataFetcher dataFetcher = fieldDef.getDataFetcher();
        dataFetcher = instrumentation.instrumentDataFetcher(dataFetcher, instrumentationFieldFetchParams);
        ExecutionId executionId = executionContext.getExecutionId();
        try {
            log.debug("'{}' fetching field '{}' using data fetcher '{}'...", executionId, fieldTypeInfo.getPath(), dataFetcher.getClass().getName());
            Object fetchedValueRaw = dataFetcher.get(environment);
            log.debug("'{}' field '{}' fetch returned '{}'", executionId, fieldTypeInfo.getPath(), fetchedValueRaw == null ? "null" : fetchedValueRaw.getClass().getName());

            fetchedValue = Async.toCompletableFuture(fetchedValueRaw);
        } catch (Exception e) {
            log.debug(String.format("'%s', field '%s' fetch threw exception", executionId, fieldTypeInfo.getPath()), e);

            fetchedValue = new CompletableFuture<>();
            fetchedValue.completeExceptionally(e);
        }
        return fetchedValue.handle((result, exception) -> {
            fetchCtx.onEnd(result, exception);
            if (exception != null) {
                handleFetchingException(executionContext, parameters, field, fieldDef, argumentValues, environment, exception);
                return null;
            } else {
                return result;
            }
        });
    }

    private void handleFetchingException(ExecutionContext executionContext,
                                         ExecutionStrategyParameters parameters,
                                         Field field,
                                         GraphQLFieldDefinition fieldDef,
                                         Map<String, Object> argumentValues,
                                         DataFetchingEnvironment environment,
                                         Throwable e) {
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

    /**
     * Called to complete a field based on the type of the field.
     * <p>
     * If the field is a scalar type, then it will be coerced  and returned.  However if the field type is an complex object type, then
     * the execution strategy will be called recursively again to execute the fields of that type before returning.
     * <p>
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
    protected CompletableFuture<ExecutionResult> completeField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Object fetchedValue) {
        Field field = parameters.field().get(0);
        GraphQLObjectType parentType = parameters.typeInfo().castType(GraphQLObjectType.class);
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field);

        InstrumentationContext<CompletableFuture<ExecutionResult>> ctx = executionContext.getInstrumentation().beginCompleteField(
                new InstrumentationFieldParameters(executionContext, fieldDef, fieldTypeInfo(parameters, fieldDef))
        );


        Map<String, Object> argumentValues = valuesResolver.getArgumentValues(fieldDef.getArguments(), field.getArguments(), executionContext.getVariables());

        ExecutionTypeInfo fieldTypeInfo = fieldTypeInfo(parameters, fieldDef);

        log.debug("'{}' completing field '{}'...", executionContext.getExecutionId(), fieldTypeInfo.getPath());

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

        CompletableFuture<ExecutionResult> cf = completeValue(executionContext, newParameters);
        ctx.onEnd(cf, null);
        return cf;
    }


    /**
     * Called to complete a value for a field based on the type of the field.
     * <p>
     * If the field is a scalar type, then it will be coerced  and returned.  However if the field type is an complex object type, then
     * the execution strategy will be called recursively again to execute the fields of that type before returning.
     * <p>
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
    protected CompletableFuture<ExecutionResult> completeValue(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        ExecutionTypeInfo typeInfo = parameters.typeInfo();
        Object result = unboxPossibleOptional(parameters.source());
        GraphQLType fieldType = typeInfo.getType();

        if (result == null) {
            return completedFuture(new ExecutionResultImpl(parameters.nonNullFieldValidator().validate(parameters.path(), null), null));
        } else if (fieldType instanceof GraphQLList) {
            return completeValueForList(executionContext, parameters, toIterable(result));
        } else if (fieldType instanceof GraphQLScalarType) {
            return completeValueForScalar(executionContext, parameters, (GraphQLScalarType) fieldType, result);
        } else if (fieldType instanceof GraphQLEnumType) {
            return completeValueForEnum(executionContext, parameters, (GraphQLEnumType) fieldType, result);
        }


        // when we are here, we have a complex type: Interface, Union or Object
        // and we must go deeper

        GraphQLObjectType resolvedType = resolveType(executionContext, parameters, fieldType);

        FieldCollectorParameters collectorParameters = newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(resolvedType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();

        Map<String, List<Field>> subFields = fieldCollector.collectFields(collectorParameters, parameters.field());

        ExecutionTypeInfo newTypeInfo = typeInfo.treatAs(resolvedType);
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

    /**
     * We treat Optional objects as "boxed" values where an empty Optional
     * equals a null object and a present Optional is the underlying value.
     *
     * @param result the incoming value
     *
     * @return an un-boxed result
     */
    protected Object unboxPossibleOptional(Object result) {
        if (result instanceof Optional) {
            Optional optional = (Optional) result;
            if (optional.isPresent()) {
                result = optional.get();
            } else {
                result = null;
            }
        }
        return result;
    }

    /**
     * Converts an object that is known to should be an Iterable into one
     *
     * @param result the result object
     *
     * @return an Iterable from that object
     *
     * @throws java.lang.ClassCastException if its not an Iterable
     */
    protected Iterable<Object> toIterable(Object result) {
        if (result.getClass().isArray()) {
            return IntStream.range(0, Array.getLength(result))
                    .mapToObj(i -> Array.get(result, i))
                    .collect(toList());
        }
        //noinspection unchecked
        return (Iterable<Object>) result;
    }

    protected GraphQLObjectType resolveType(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLType fieldType) {
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
        return resolvedType;
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
            throw new UnresolvedTypeException(params.getGraphQLInterfaceType());
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
            throw new UnresolvedTypeException(params.getGraphQLUnionType());
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
    protected CompletableFuture<ExecutionResult> completeValueForEnum(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLEnumType enumType, Object result) {
        Object serialized;
        try {
            serialized = enumType.getCoercing().serialize(result);
        } catch (CoercingSerializeException e) {
            serialized = handleCoercionProblem(executionContext, parameters, e);
        }
        serialized = parameters.nonNullFieldValidator().validate(parameters.path(), serialized);
        return completedFuture(new ExecutionResultImpl(serialized, null));
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
    protected CompletableFuture<ExecutionResult> completeValueForScalar(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLScalarType scalarType, Object result) {
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
        return completedFuture(new ExecutionResultImpl(serialized, null));
    }

    private Object handleCoercionProblem(ExecutionContext context, ExecutionStrategyParameters parameters, CoercingSerializeException e) {
        SerializationError error = new SerializationError(parameters.path(), e);
        log.warn(error.getMessage(), e);
        context.addError(error, parameters.path());
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
    protected CompletableFuture<ExecutionResult> completeValueForList(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Iterable<Object> iterableValues) {

        ExecutionTypeInfo typeInfo = parameters.typeInfo();
        GraphQLList fieldType = typeInfo.castType(GraphQLList.class);
        GraphQLFieldDefinition fieldDef = parameters.typeInfo().getFieldDefinition();

        InstrumentationContext<CompletableFuture<ExecutionResult>> ctx = executionContext.getInstrumentation().beginCompleteFieldList(
                new InstrumentationFieldParameters(executionContext, fieldDef, fieldTypeInfo(parameters, fieldDef))
        );

        CompletableFuture<List<ExecutionResult>> resultsFuture = Async.each(iterableValues, (item, index) -> {

            ExecutionPath indexedPath = parameters.path().segment(index);

            ExecutionTypeInfo wrappedTypeInfo = ExecutionTypeInfo.newTypeInfo()
                    .parentInfo(typeInfo)
                    .type(fieldType.getWrappedType())
                    .path(indexedPath)
                    .fieldDefinition(fieldDef)
                    .build();

            NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, wrappedTypeInfo);

            ExecutionStrategyParameters newParameters = ExecutionStrategyParameters.newParameters()
                    .typeInfo(wrappedTypeInfo)
                    .fields(parameters.fields())
                    .nonNullFieldValidator(nonNullableFieldValidator)
                    .path(indexedPath)
                    .field(parameters.field())
                    .source(item)
                    .build();

            return completeValue(executionContext, newParameters);
        });
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        resultsFuture.whenComplete((results, exception) -> {
            if (exception != null) {
                handleNonNullException(executionContext, overallResult, exception);
                return;
            }
            List<Object> completedResults = new ArrayList<>();
            for (ExecutionResult completedValue : results) {
                completedResults.add(completedValue.getData());
            }
            overallResult.complete(new ExecutionResultImpl(completedResults, null));
        });
        ctx.onEnd(overallResult, null);
        return overallResult;
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

        GraphQLFieldDefinition fieldDefinition = schema.getFieldVisibility().getFieldDefinition(parentType, field.getName());
        Assert.assertTrue(fieldDefinition != null, "Unknown field " + field.getName());
        return fieldDefinition;
    }

    /**
     * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability),
     * <p>
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
        ExecutionTypeInfo typeInfo = e.getTypeInfo();
        if (typeInfo.hasParentType() && typeInfo.getParentTypeInfo().isNonNullType()) {
            throw new NonNullableFieldWasNullException(e);
        }
    }

    protected void assertNonNullFieldPrecondition(NonNullableFieldWasNullException e, CompletableFuture<?> completableFuture) throws NonNullableFieldWasNullException {
        ExecutionTypeInfo typeInfo = e.getTypeInfo();
        if (typeInfo.hasParentType() && typeInfo.getParentTypeInfo().isNonNullType()) {
            completableFuture.completeExceptionally(new NonNullableFieldWasNullException(e));
        }
    }

    protected void handleNonNullException(ExecutionContext executionContext, CompletableFuture<ExecutionResult> result, Throwable e) {
        if (e instanceof NonNullableFieldWasNullException) {
            assertNonNullFieldPrecondition((NonNullableFieldWasNullException) e, result);
            if (!result.isDone()) {
                result.complete(new ExecutionResultImpl(null, executionContext.getErrors()));
            }
        } else if (e instanceof CompletionException && e.getCause() instanceof NonNullableFieldWasNullException) {
            assertNonNullFieldPrecondition((NonNullableFieldWasNullException) e.getCause(), result);
            if (!result.isDone()) {
                result.complete(new ExecutionResultImpl(null, executionContext.getErrors()));
            }
        } else {
            result.completeExceptionally(e);
        }
    }


    /**
     * Builds the type info hierarchy for the current field
     *
     * @param parameters      contains the parameters holding the fields to be executed and source object
     * @param fieldDefinition the field definition to build type info for
     *
     * @return a new type info
     */
    protected ExecutionTypeInfo fieldTypeInfo(ExecutionStrategyParameters parameters, GraphQLFieldDefinition fieldDefinition) {
        GraphQLOutputType fieldType = fieldDefinition.getType();
        return newTypeInfo()
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .path(parameters.path())
                .parentInfo(parameters.typeInfo())
                .build();

    }
}
