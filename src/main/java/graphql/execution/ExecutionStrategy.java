package graphql.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import graphql.DuckTyped;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.ExperimentalApi;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.PublicSpi;
import graphql.SerializationError;
import graphql.TrivialDataFetcher;
import graphql.TypeMismatchError;
import graphql.UnresolvedTypeError;
import graphql.collect.ImmutableMapWithNullValues;
import graphql.execution.directives.QueryDirectives;
import graphql.execution.directives.QueryDirectivesImpl;
import graphql.execution.incremental.DeferredExecutionSupport;
import graphql.execution.instrumentation.ExecuteObjectInstrumentationContext;
import graphql.execution.instrumentation.FieldFetchingInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.reactive.ReactiveSupport;
import graphql.extensions.ExtensionsBuilder;
import graphql.introspection.Introspection;
import graphql.language.Argument;
import graphql.language.Field;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.LightDataFetcher;
import graphql.util.FpKit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static graphql.execution.Async.exceptionallyCompletedFuture;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;
import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.execution.FieldValueInfo.CompleteValueType.ENUM;
import static graphql.execution.FieldValueInfo.CompleteValueType.LIST;
import static graphql.execution.FieldValueInfo.CompleteValueType.NULL;
import static graphql.execution.FieldValueInfo.CompleteValueType.OBJECT;
import static graphql.execution.FieldValueInfo.CompleteValueType.SCALAR;
import static graphql.execution.ResultNodesInfo.MAX_RESULT_NODES;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.nonNullCtx;
import static graphql.schema.DataFetchingEnvironmentImpl.newDataFetchingEnvironment;
import static graphql.schema.GraphQLTypeUtil.isEnum;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isScalar;

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
 * coercion or if it's a complex object type by recursively calling the execution strategy for the lower level fields.
 * <p>
 * The first phase (data fetching) is handled by the method {@link #fetchField(ExecutionContext, ExecutionStrategyParameters)}
 * <p>
 * The second phase (value completion) is handled by the methods {@link #completeField(ExecutionContext, ExecutionStrategyParameters, FetchedValue)}
 * and the other "completeXXX" methods.
 * <p>
 * The order of fields fetching and completion is up to the execution strategy. As the graphql specification
 * <a href="https://spec.graphql.org/October2021/#sec-Normal-and-Serial-Execution">https://spec.graphql.org/October2021/#sec-Normal-and-Serial-Execution</a> says:
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
@SuppressWarnings("FutureReturnValueIgnored")
public abstract class ExecutionStrategy {

    protected final FieldCollector fieldCollector = new FieldCollector();
    protected final ExecutionStepInfoFactory executionStepInfoFactory = new ExecutionStepInfoFactory();
    protected final DataFetcherExceptionHandler dataFetcherExceptionHandler;
    private final ResolveType resolvedType = new ResolveType();


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


    @Internal
    public static String mkNameForPath(Field currentField) {
        return mkNameForPath(Collections.singletonList(currentField));
    }

    @Internal
    public static String mkNameForPath(MergedField mergedField) {
        return mkNameForPath(mergedField.getFields());
    }

    @Internal
    public static String mkNameForPath(List<Field> currentField) {
        Field field = currentField.get(0);
        return field.getResultKey();
    }

    /**
     * This is the entry point to an execution strategy.  It will be passed the fields to execute and get values for.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return a promise to an {@link ExecutionResult}
     *
     * @throws NonNullableFieldWasNullException in the future if a non-null field resolves to a null value
     */
    public abstract CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException;

    /**
     * This is the re-entry point for an execution strategy when an object type needs to be resolved.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return a {@link CompletableFuture} promise to a map of object field values or a materialized map of object field values
     *
     * @throws NonNullableFieldWasNullException in the {@link CompletableFuture} if a non-null field resolved to a null value
     */
    @SuppressWarnings("unchecked")
    @DuckTyped(shape = "CompletableFuture<Map<String, Object>> | Map<String, Object>")
    protected Object executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = executionContext.getDataLoaderDispatcherStrategy();
        dataLoaderDispatcherStrategy.executeObject(executionContext, parameters);
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecuteObjectInstrumentationContext resolveObjectCtx = ExecuteObjectInstrumentationContext.nonNullCtx(
                instrumentation.beginExecuteObject(instrumentationParameters, executionContext.getInstrumentationState())
        );

        List<String> fieldNames = parameters.getFields().getKeys();

        DeferredExecutionSupport deferredExecutionSupport = createDeferredExecutionSupport(executionContext, parameters);
        Async.CombinedBuilder<FieldValueInfo> resolvedFieldFutures = getAsyncFieldValueInfo(executionContext, parameters, deferredExecutionSupport);

        CompletableFuture<Map<String, Object>> overallResult = new CompletableFuture<>();
        List<String> fieldsExecutedOnInitialResult = deferredExecutionSupport.getNonDeferredFieldNames(fieldNames);
        BiConsumer<List<Object>, Throwable> handleResultsConsumer = buildFieldValueMap(fieldsExecutedOnInitialResult, overallResult, executionContext);

        resolveObjectCtx.onDispatched();

        Object fieldValueInfosResult = resolvedFieldFutures.awaitPolymorphic();
        if (fieldValueInfosResult instanceof CompletableFuture) {
            CompletableFuture<List<FieldValueInfo>> fieldValueInfos = (CompletableFuture<List<FieldValueInfo>>) fieldValueInfosResult;
            fieldValueInfos.whenComplete((completeValueInfos, throwable) -> {
                if (throwable != null) {
                    handleResultsConsumer.accept(null, throwable);
                    return;
                }

                Async.CombinedBuilder<Object> resultFutures = fieldValuesCombinedBuilder(completeValueInfos);
                dataLoaderDispatcherStrategy.executeObjectOnFieldValuesInfo(completeValueInfos, parameters);
                resolveObjectCtx.onFieldValuesInfo(completeValueInfos);
                resultFutures.await().whenComplete(handleResultsConsumer);
            }).exceptionally((ex) -> {
                // if there are any issues with combining/handling the field results,
                // complete the future at all costs and bubble up any thrown exception so
                // the execution does not hang.
                dataLoaderDispatcherStrategy.executeObjectOnFieldValuesException(ex, parameters);
                resolveObjectCtx.onFieldValuesException();
                overallResult.completeExceptionally(ex);
                return null;
            });
            overallResult.whenComplete(resolveObjectCtx::onCompleted);
            return overallResult;
        } else {
            List<FieldValueInfo> completeValueInfos = (List<FieldValueInfo>) fieldValueInfosResult;

            Async.CombinedBuilder<Object> resultFutures = fieldValuesCombinedBuilder(completeValueInfos);
            dataLoaderDispatcherStrategy.executeObjectOnFieldValuesInfo(completeValueInfos, parameters);
            resolveObjectCtx.onFieldValuesInfo(completeValueInfos);

            Object completedValuesObject = resultFutures.awaitPolymorphic();
            if (completedValuesObject instanceof CompletableFuture) {
                CompletableFuture<List<Object>> completedValues = (CompletableFuture<List<Object>>) completedValuesObject;
                completedValues.whenComplete(handleResultsConsumer);
                overallResult.whenComplete(resolveObjectCtx::onCompleted);
                return overallResult;
            } else {
                Map<String, Object> fieldValueMap = buildFieldValueMap(fieldsExecutedOnInitialResult, (List<Object>) completedValuesObject);
                resolveObjectCtx.onCompleted(fieldValueMap, null);
                return fieldValueMap;
            }
        }
    }

    @NotNull
    private static Async.CombinedBuilder<Object> fieldValuesCombinedBuilder(List<FieldValueInfo> completeValueInfos) {
        Async.CombinedBuilder<Object> resultFutures = Async.ofExpectedSize(completeValueInfos.size());
        for (FieldValueInfo completeValueInfo : completeValueInfos) {
            resultFutures.addObject(completeValueInfo.getFieldValueObject());
        }
        return resultFutures;
    }

    private BiConsumer<List<Object>, Throwable> buildFieldValueMap(List<String> fieldNames, CompletableFuture<Map<String, Object>> overallResult, ExecutionContext executionContext) {
        return (List<Object> results, Throwable exception) -> {
            if (exception != null) {
                handleValueException(overallResult, exception, executionContext);
                return;
            }
            Map<String, Object> resolvedValuesByField = buildFieldValueMap(fieldNames, results);
            overallResult.complete(resolvedValuesByField);
        };
    }

    @NotNull
    private static Map<String, Object> buildFieldValueMap(List<String> fieldNames, List<Object> results) {
        Map<String, Object> resolvedValuesByField = Maps.newLinkedHashMapWithExpectedSize(fieldNames.size());
        int ix = 0;
        for (Object fieldValue : results) {
            String fieldName = fieldNames.get(ix++);
            resolvedValuesByField.put(fieldName, fieldValue);
        }
        return resolvedValuesByField;
    }

    DeferredExecutionSupport createDeferredExecutionSupport(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        MergedSelectionSet fields = parameters.getFields();

        return Optional.ofNullable(executionContext.getGraphQLContext())
                .map(graphqlContext -> graphqlContext.getBoolean(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT))
                .orElse(false) ?
                new DeferredExecutionSupport.DeferredExecutionSupportImpl(
                        fields,
                        parameters,
                        executionContext,
                        (ec, esp) -> Async.toCompletableFuture(resolveFieldWithInfo(ec, esp))
                ) : DeferredExecutionSupport.NOOP;

    }

    @NotNull
    Async.CombinedBuilder<FieldValueInfo> getAsyncFieldValueInfo(
            ExecutionContext executionContext,
            ExecutionStrategyParameters parameters,
            DeferredExecutionSupport deferredExecutionSupport
    ) {
        MergedSelectionSet fields = parameters.getFields();

        executionContext.getIncrementalCallState().enqueue(deferredExecutionSupport.createCalls(parameters));

        // Only non-deferred fields should be considered for calculating the expected size of futures.
        Async.CombinedBuilder<FieldValueInfo> futures = Async
                .ofExpectedSize(fields.size() - deferredExecutionSupport.deferredFieldsCount());

        for (String fieldName : fields.getKeys()) {
            MergedField currentField = fields.getSubField(fieldName);

            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));

            if (!deferredExecutionSupport.isDeferredField(currentField)) {
                Object fieldValueInfo = resolveFieldWithInfo(executionContext, newParameters);
                futures.addObject(fieldValueInfo);
            }
        }
        return futures;
    }

    /**
     * Called to fetch a value for a field and resolve it further in terms of the graphql query.  This will call
     * #fetchField followed by #completeField and the completed Object is returned.
     * <p>
     * An execution strategy can iterate the fields to be executed and call this method for each one
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However, the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return a {@link CompletableFuture} promise to an {@link Object} or the materialized {@link Object}
     *
     * @throws NonNullableFieldWasNullException in the future if a non-null field resolved to a null value
     */
    @SuppressWarnings("unchecked")
    @DuckTyped(shape = " CompletableFuture<Object> | Object")
    protected Object resolveField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        Object fieldWithInfo = resolveFieldWithInfo(executionContext, parameters);
        if (fieldWithInfo instanceof CompletableFuture) {
            return ((CompletableFuture<FieldValueInfo>) fieldWithInfo).thenCompose(FieldValueInfo::getFieldValueFuture);
        } else {
            return ((FieldValueInfo) fieldWithInfo).getFieldValueObject();
        }
    }

    /**
     * Called to fetch a value for a field and its extra runtime info and resolve it further in terms of the graphql query.  This will call
     * #fetchField followed by #completeField and the completed {@link graphql.execution.FieldValueInfo} is returned.
     * <p>
     * An execution strategy can iterate the fields to be executed and call this method for each one
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return a {@link CompletableFuture} promise to a {@link FieldValueInfo} or a materialised {@link FieldValueInfo}
     *
     * @throws NonNullableFieldWasNullException in the {@link FieldValueInfo#getFieldValueFuture()} future
     *                                          if a nonnull field resolves to a null value
     */
    @SuppressWarnings("unchecked")
    @DuckTyped(shape = "CompletableFuture<FieldValueInfo> | FieldValueInfo")
    protected Object resolveFieldWithInfo(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext, parameters, parameters.getField().getSingleField());
        Supplier<ExecutionStepInfo> executionStepInfo = FpKit.intraThreadMemoize(() -> createExecutionStepInfo(executionContext, parameters, fieldDef, null));

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationContext<Object> fieldCtx = nonNullCtx(instrumentation.beginFieldExecution(
                new InstrumentationFieldParameters(executionContext, executionStepInfo), executionContext.getInstrumentationState()
        ));

        Object fetchedValueObj = fetchField(executionContext, parameters);
        if (fetchedValueObj instanceof CompletableFuture) {
            CompletableFuture<FetchedValue> fetchFieldFuture = (CompletableFuture<FetchedValue>) fetchedValueObj;
            CompletableFuture<FieldValueInfo> result = fetchFieldFuture.thenApply((fetchedValue) ->
                    completeField(fieldDef, executionContext, parameters, fetchedValue));

            fieldCtx.onDispatched();
            result.whenComplete(fieldCtx::onCompleted);
            return result;
        } else {
            try {
                FetchedValue fetchedValue = (FetchedValue) fetchedValueObj;
                FieldValueInfo fieldValueInfo = completeField(fieldDef, executionContext, parameters, fetchedValue);
                fieldCtx.onDispatched();
                fieldCtx.onCompleted(fetchedValue.getFetchedValue(), null);
                return fieldValueInfo;
            } catch (Exception e) {
                return Async.exceptionallyCompletedFuture(e);
            }
        }
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
     * @return a promise to a {@link FetchedValue} object or the {@link FetchedValue} itself
     *
     * @throws NonNullableFieldWasNullException in the future if a non null field resolves to a null value
     */
    @DuckTyped(shape = "CompletableFuture<FetchedValue> | FetchedValue")
    protected Object fetchField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        MergedField field = parameters.getField();
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field.getSingleField());
        return fetchField(fieldDef, executionContext, parameters);
    }

    @DuckTyped(shape = "CompletableFuture<FetchedValue> | FetchedValue")
    private Object fetchField(GraphQLFieldDefinition fieldDef, ExecutionContext executionContext, ExecutionStrategyParameters parameters) {

        if (incrementAndCheckMaxNodesExceeded(executionContext)) {
            return new FetchedValue(null, Collections.emptyList(), null);
        }

        MergedField field = parameters.getField();
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();

        // if the DF (like PropertyDataFetcher) does not use the arguments or execution step info then dont build any

        Supplier<DataFetchingEnvironment> dataFetchingEnvironment = FpKit.intraThreadMemoize(() -> {

            Supplier<ExecutionStepInfo> executionStepInfo = FpKit.intraThreadMemoize(
                    () -> createExecutionStepInfo(executionContext, parameters, fieldDef, parentType));

            Supplier<Map<String, Object>> argumentValues = () -> executionStepInfo.get().getArguments();

            Supplier<ExecutableNormalizedField> normalizedFieldSupplier = getNormalizedField(executionContext, parameters, executionStepInfo);

            // DataFetchingFieldSelectionSet and QueryDirectives is a supplier of sorts - eg a lazy pattern
            DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext.getGraphQLSchema(), fieldDef.getType(), normalizedFieldSupplier);
            QueryDirectives queryDirectives = new QueryDirectivesImpl(field,
                    executionContext.getGraphQLSchema(),
                    executionContext.getCoercedVariables().toMap(),
                    executionContext.getGraphQLContext(),
                    executionContext.getLocale());


            return newDataFetchingEnvironment(executionContext)
                    .source(parameters.getSource())
                    .localContext(parameters.getLocalContext())
                    .arguments(argumentValues)
                    .fieldDefinition(fieldDef)
                    .mergedField(parameters.getField())
                    .fieldType(fieldDef.getType())
                    .executionStepInfo(executionStepInfo)
                    .parentType(parentType)
                    .selectionSet(fieldCollector)
                    .queryDirectives(queryDirectives)
                    .build();
        });

        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parentType, fieldDef);

        Instrumentation instrumentation = executionContext.getInstrumentation();

        InstrumentationFieldFetchParameters instrumentationFieldFetchParams = new InstrumentationFieldFetchParameters(executionContext, dataFetchingEnvironment, parameters, dataFetcher instanceof TrivialDataFetcher);
        FieldFetchingInstrumentationContext fetchCtx = FieldFetchingInstrumentationContext.nonNullCtx(instrumentation.beginFieldFetching(instrumentationFieldFetchParams,
                executionContext.getInstrumentationState())
        );

        dataFetcher = instrumentation.instrumentDataFetcher(dataFetcher, instrumentationFieldFetchParams, executionContext.getInstrumentationState());
        dataFetcher = executionContext.getDataLoaderDispatcherStrategy().modifyDataFetcher(dataFetcher);
        Object fetchedObject = invokeDataFetcher(executionContext, parameters, fieldDef, dataFetchingEnvironment, dataFetcher);
        executionContext.getDataLoaderDispatcherStrategy().fieldFetched(executionContext, parameters, dataFetcher, fetchedObject);
        fetchCtx.onDispatched();
        fetchCtx.onFetchedValue(fetchedObject);
        // if it's a subscription, leave any reactive objects alone
        if (!executionContext.isSubscriptionOperation()) {
            // possible convert reactive objects into CompletableFutures
            fetchedObject = ReactiveSupport.fetchedObject(fetchedObject);
        }
        if (fetchedObject instanceof CompletableFuture) {
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> fetchedValue = (CompletableFuture<Object>) fetchedObject;
            return fetchedValue
                    .handle((result, exception) -> {
                        fetchCtx.onCompleted(result, exception);
                        if (exception != null) {
                            return handleFetchingException(dataFetchingEnvironment.get(), parameters, exception);
                        } else {
                            // we can simply return the fetched value CF and avoid a allocation
                            return fetchedValue;
                        }
                    })
                    .thenCompose(Function.identity())
                    .thenApply(result -> unboxPossibleDataFetcherResult(executionContext, parameters, result));
        } else {
            fetchCtx.onCompleted(fetchedObject, null);
            return unboxPossibleDataFetcherResult(executionContext, parameters, fetchedObject);
        }
    }

    /*
     * ExecutionContext is not used in the method, but the java agent uses it, so it needs to be present
     */
    @SuppressWarnings("unused")
    private Object invokeDataFetcher(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLFieldDefinition fieldDef, Supplier<DataFetchingEnvironment> dataFetchingEnvironment, DataFetcher<?> dataFetcher) {
        Object fetchedValue;
        try {
            Object fetchedValueRaw;
            if (dataFetcher instanceof LightDataFetcher) {
                fetchedValueRaw = ((LightDataFetcher<?>) dataFetcher).get(fieldDef, parameters.getSource(), dataFetchingEnvironment);
            } else {
                fetchedValueRaw = dataFetcher.get(dataFetchingEnvironment.get());
            }
            fetchedValue = Async.toCompletableFutureOrMaterializedObject(fetchedValueRaw);
        } catch (Exception e) {
            fetchedValue = Async.exceptionallyCompletedFuture(e);
        }
        return fetchedValue;
    }

    protected Supplier<ExecutableNormalizedField> getNormalizedField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Supplier<ExecutionStepInfo> executionStepInfo) {
        Supplier<ExecutableNormalizedOperation> normalizedQuery = executionContext.getNormalizedQueryTree();
        return () -> normalizedQuery.get().getNormalizedField(parameters.getField(), executionStepInfo.get().getObjectType(), executionStepInfo.get().getPath());
    }

    protected FetchedValue unboxPossibleDataFetcherResult(ExecutionContext executionContext,
                                                          ExecutionStrategyParameters parameters,
                                                          Object result) {

        if (result instanceof DataFetcherResult) {
            DataFetcherResult<?> dataFetcherResult = (DataFetcherResult<?>) result;

            addErrorsToRightContext(dataFetcherResult.getErrors(), parameters, executionContext);

            addExtensionsIfPresent(executionContext, dataFetcherResult);

            Object localContext = dataFetcherResult.getLocalContext();
            if (localContext == null) {
                // if the field returns nothing then they get the context of their parent field
                localContext = parameters.getLocalContext();
            }
            Object unBoxedValue = executionContext.getValueUnboxer().unbox(dataFetcherResult.getData());
            return new FetchedValue(unBoxedValue, dataFetcherResult.getErrors(), localContext);
        } else {
            Object unBoxedValue = executionContext.getValueUnboxer().unbox(result);
            return new FetchedValue(unBoxedValue, ImmutableList.of(), parameters.getLocalContext());
        }
    }

    private void addExtensionsIfPresent(ExecutionContext executionContext, DataFetcherResult<?> dataFetcherResult) {
        Map<Object, Object> extensions = dataFetcherResult.getExtensions();
        if (extensions != null) {
            ExtensionsBuilder extensionsBuilder = executionContext.getGraphQLContext().get(ExtensionsBuilder.class);
            if (extensionsBuilder != null) {
                extensionsBuilder.addValues(extensions);
            }
        }
    }

    protected <T> CompletableFuture<T> handleFetchingException(
            DataFetchingEnvironment environment,
            ExecutionStrategyParameters parameters,
            Throwable e
    ) {
        DataFetcherExceptionHandlerParameters handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .dataFetchingEnvironment(environment)
                .exception(e)
                .build();

        try {
            return asyncHandleException(dataFetcherExceptionHandler, handlerParameters);
        } catch (Exception handlerException) {
            handlerParameters = DataFetcherExceptionHandlerParameters.newExceptionParameters()
                    .dataFetchingEnvironment(environment)
                    .exception(handlerException)
                    .build();
            return asyncHandleException(new SimpleDataFetcherExceptionHandler(), handlerParameters);
        }
    }

    private <T> CompletableFuture<T> asyncHandleException(DataFetcherExceptionHandler handler, DataFetcherExceptionHandlerParameters handlerParameters) {
        //noinspection unchecked
        return handler.handleException(handlerParameters).thenApply(
                handlerResult -> (T) DataFetcherResult.<FetchedValue>newResult().errors(handlerResult.getErrors()).build()
        );
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
     * @return a {@link FieldValueInfo}
     *
     * @throws NonNullableFieldWasNullException in the {@link FieldValueInfo#getFieldValueFuture()} future
     *                                          if a nonnull field resolves to a null value
     */
    protected FieldValueInfo completeField(ExecutionContext executionContext, ExecutionStrategyParameters parameters, FetchedValue fetchedValue) {
        Field field = parameters.getField().getSingleField();
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field);
        return completeField(fieldDef, executionContext, parameters, fetchedValue);
    }

    private FieldValueInfo completeField(GraphQLFieldDefinition fieldDef, ExecutionContext executionContext, ExecutionStrategyParameters parameters, FetchedValue fetchedValue) {
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();
        ExecutionStepInfo executionStepInfo = createExecutionStepInfo(executionContext, parameters, fieldDef, parentType);

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationFieldCompleteParameters instrumentationParams = new InstrumentationFieldCompleteParameters(executionContext, parameters, () -> executionStepInfo, fetchedValue);
        InstrumentationContext<Object> ctxCompleteField = nonNullCtx(instrumentation.beginFieldCompletion(
                instrumentationParams, executionContext.getInstrumentationState()
        ));

        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo);

        ExecutionStrategyParameters newParameters = parameters.transform(builder ->
                builder.executionStepInfo(executionStepInfo)
                        .source(fetchedValue.getFetchedValue())
                        .localContext(fetchedValue.getLocalContext())
                        .nonNullFieldValidator(nonNullableFieldValidator)
        );

        FieldValueInfo fieldValueInfo = completeValue(executionContext, newParameters);

        CompletableFuture<Object> executionResultFuture = fieldValueInfo.getFieldValueFuture();
        ctxCompleteField.onDispatched();
        executionResultFuture.whenComplete(ctxCompleteField::onCompleted);
        return fieldValueInfo;
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
     * @return a {@link FieldValueInfo}
     *
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected FieldValueInfo completeValue(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        ExecutionStepInfo executionStepInfo = parameters.getExecutionStepInfo();
        Object result = executionContext.getValueUnboxer().unbox(parameters.getSource());
        GraphQLType fieldType = executionStepInfo.getUnwrappedNonNullType();
        Object fieldValue;

        if (result == null) {
            return getFieldValueInfoForNull(parameters);
        } else if (isList(fieldType)) {
            return completeValueForList(executionContext, parameters, result);
        } else if (isScalar(fieldType)) {
            fieldValue = completeValueForScalar(executionContext, parameters, (GraphQLScalarType) fieldType, result);
            return new FieldValueInfo(SCALAR, fieldValue);
        } else if (isEnum(fieldType)) {
            fieldValue = completeValueForEnum(executionContext, parameters, (GraphQLEnumType) fieldType, result);
            return new FieldValueInfo(ENUM, fieldValue);
        }

        // when we are here, we have a complex type: Interface, Union or Object
        // and we must go deeper
        //
        GraphQLObjectType resolvedObjectType;
        try {
            resolvedObjectType = resolveType(executionContext, parameters, fieldType);
            fieldValue = completeValueForObject(executionContext, parameters, resolvedObjectType, result);
        } catch (UnresolvedTypeException ex) {
            // consider the result to be null and add the error on the context
            handleUnresolvedTypeProblem(executionContext, parameters, ex);
            // complete field as null, validating it is nullable
            return getFieldValueInfoForNull(parameters);
        }
        return new FieldValueInfo(OBJECT, fieldValue);
    }

    private void handleUnresolvedTypeProblem(ExecutionContext context, ExecutionStrategyParameters parameters, UnresolvedTypeException e) {
        UnresolvedTypeError error = new UnresolvedTypeError(parameters.getPath(), parameters.getExecutionStepInfo(), e);

        addErrorToRightContext(error, parameters, context);
    }

    /**
     * Called to complete a null value.
     *
     * @param parameters contains the parameters holding the fields to be executed and source object
     *
     * @return a {@link FieldValueInfo}
     *
     * @throws NonNullableFieldWasNullException inside a {@link CompletableFuture} if a non null field resolves to a null value
     */
    private FieldValueInfo getFieldValueInfoForNull(ExecutionStrategyParameters parameters) {
        Object fieldValue = completeValueForNull(parameters);
        return new FieldValueInfo(NULL, fieldValue);
    }

    /**
     * Called to complete a null value.
     *
     * @param parameters contains the parameters holding the fields to be executed and source object
     *
     * @return a null value or a {@link CompletableFuture} exceptionally completed
     *
     * @throws NonNullableFieldWasNullException inside the {@link CompletableFuture} if a non-null field resolves to a null value
     */
    @DuckTyped(shape = "CompletableFuture<Object> | Object")
    protected Object completeValueForNull(ExecutionStrategyParameters parameters) {
        try {
            return parameters.getNonNullFieldValidator().validate(parameters, null);
        } catch (Exception e) {
            return Async.exceptionallyCompletedFuture(e);
        }
    }

    /**
     * Called to complete a list of value for a field based on a list type.  This iterates the values and calls
     * {@link #completeValue(ExecutionContext, ExecutionStrategyParameters)} for each value.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param result           the result to complete, raw result
     *
     * @return a {@link FieldValueInfo}
     */
    protected FieldValueInfo completeValueForList(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Object result) {
        Iterable<Object> resultIterable = toIterable(executionContext, parameters, result);
        try {
            resultIterable = parameters.getNonNullFieldValidator().validate(parameters, resultIterable);
        } catch (NonNullableFieldWasNullException e) {
            return new FieldValueInfo(LIST, exceptionallyCompletedFuture(e));
        }
        if (resultIterable == null) {
            return new FieldValueInfo(LIST, null);
        }
        return completeValueForList(executionContext, parameters, resultIterable);
    }

    /**
     * Called to complete a list of value for a field based on a list type.  This iterates the values and calls
     * {@link #completeValue(ExecutionContext, ExecutionStrategyParameters)} for each value.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param iterableValues   the values to complete, can't be null
     *
     * @return a {@link FieldValueInfo}
     */
    protected FieldValueInfo completeValueForList(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Iterable<Object> iterableValues) {

        OptionalInt size = FpKit.toSize(iterableValues);
        ExecutionStepInfo executionStepInfo = parameters.getExecutionStepInfo();

        InstrumentationFieldCompleteParameters instrumentationParams = new InstrumentationFieldCompleteParameters(executionContext, parameters, () -> executionStepInfo, iterableValues);
        Instrumentation instrumentation = executionContext.getInstrumentation();

        InstrumentationContext<Object> completeListCtx = nonNullCtx(instrumentation.beginFieldListCompletion(
                instrumentationParams, executionContext.getInstrumentationState()
        ));

        List<FieldValueInfo> fieldValueInfos = new ArrayList<>(size.orElse(1));
        int index = 0;
        for (Object item : iterableValues) {
            if (incrementAndCheckMaxNodesExceeded(executionContext)) {
                return new FieldValueInfo(NULL, null, fieldValueInfos);
            }

            ResultPath indexedPath = parameters.getPath().segment(index);

            ExecutionStepInfo stepInfoForListElement = executionStepInfoFactory.newExecutionStepInfoForListElement(executionStepInfo, indexedPath);

            NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, stepInfoForListElement);

            FetchedValue value = unboxPossibleDataFetcherResult(executionContext, parameters, item);

            ExecutionStrategyParameters newParameters = parameters.transform(builder ->
                    builder.executionStepInfo(stepInfoForListElement)
                            .nonNullFieldValidator(nonNullableFieldValidator)
                            .localContext(value.getLocalContext())
                            .path(indexedPath)
                            .source(value.getFetchedValue())
            );
            fieldValueInfos.add(completeValue(executionContext, newParameters));
            index++;
        }

        Object listResults = Async.eachPolymorphic(fieldValueInfos, FieldValueInfo::getFieldValueObject);
        Object listOrPromiseToList;
        if (listResults instanceof CompletableFuture) {
            @SuppressWarnings("unchecked")
            CompletableFuture<List<Object>> resultsFuture = (CompletableFuture<List<Object>>) listResults;
            CompletableFuture<Object> overallResult = new CompletableFuture<>();
            completeListCtx.onDispatched();
            overallResult.whenComplete(completeListCtx::onCompleted);

            resultsFuture.whenComplete((results, exception) -> {
                if (exception != null) {
                    handleValueException(overallResult, exception, executionContext);
                    return;
                }
                List<Object> completedResults = new ArrayList<>(results.size());
                completedResults.addAll(results);
                overallResult.complete(completedResults);
            });
            listOrPromiseToList = overallResult;
        } else {
            completeListCtx.onCompleted(listResults, null);
            listOrPromiseToList = listResults;
        }
        return new FieldValueInfo(LIST, listOrPromiseToList, fieldValueInfos);
    }

    protected <T> void handleValueException(CompletableFuture<T> overallResult, Throwable e, ExecutionContext executionContext) {
        Throwable underlyingException = e;
        if (e instanceof CompletionException) {
            underlyingException = e.getCause();
        }
        if (underlyingException instanceof NonNullableFieldWasNullException) {
            assertNonNullFieldPrecondition((NonNullableFieldWasNullException) underlyingException, overallResult);
            if (!overallResult.isDone()) {
                overallResult.complete(null);
            }
        } else if (underlyingException instanceof AbortExecutionException) {
            AbortExecutionException abortException = (AbortExecutionException) underlyingException;
            executionContext.addError(abortException);
            if (!overallResult.isDone()) {
                overallResult.complete(null);
            }
        } else {
            overallResult.completeExceptionally(e);
        }
    }


    /**
     * Called to turn an object into a scalar value according to the {@link GraphQLScalarType} by asking that scalar type to coerce the object
     * into a valid value
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param scalarType       the type of the scalar
     * @param result           the result to be coerced
     *
     * @return a materialized scalar value or exceptionally completed {@link CompletableFuture} if there is a problem
     */
    @DuckTyped(shape = "CompletableFuture<Object> | Object")
    protected Object completeValueForScalar(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLScalarType scalarType, Object result) {
        Object serialized;
        try {
            serialized = scalarType.getCoercing().serialize(result, executionContext.getGraphQLContext(), executionContext.getLocale());
        } catch (CoercingSerializeException e) {
            serialized = handleCoercionProblem(executionContext, parameters, e);
        }

        try {
            serialized = parameters.getNonNullFieldValidator().validate(parameters, serialized);
        } catch (NonNullableFieldWasNullException e) {
            return exceptionallyCompletedFuture(e);
        }
        return serialized;
    }

    /**
     * Called to turn an object into an enum value according to the {@link GraphQLEnumType} by asking that enum type to coerce the object into a valid value
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param enumType         the type of the enum
     * @param result           the result to be coerced
     *
     * @return a materialized enum value or exceptionally completed {@link CompletableFuture} if there is a problem
     */
    @DuckTyped(shape = "CompletableFuture<Object> | Object")
    protected Object completeValueForEnum(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLEnumType enumType, Object result) {
        Object serialized;
        try {
            serialized = enumType.serialize(result, executionContext.getGraphQLContext(), executionContext.getLocale());
        } catch (CoercingSerializeException e) {
            serialized = handleCoercionProblem(executionContext, parameters, e);
        }
        try {
            serialized = parameters.getNonNullFieldValidator().validate(parameters, serialized);
        } catch (NonNullableFieldWasNullException e) {
            return exceptionallyCompletedFuture(e);
        }
        return serialized;
    }

    /**
     * Called to turn a java object value into an graphql object value
     *
     * @param executionContext   contains the top level execution parameters
     * @param parameters         contains the parameters holding the fields to be executed and source object
     * @param resolvedObjectType the resolved object type
     * @param result             the result to be coerced
     *
     * @return a {@link CompletableFuture} promise to a map of object field values or a materialized map of object field values
     */
    @DuckTyped(shape = "CompletableFuture<Map<String, Object>> | Map<String, Object>")
    protected Object completeValueForObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLObjectType resolvedObjectType, Object result) {
        ExecutionStepInfo executionStepInfo = parameters.getExecutionStepInfo();

        FieldCollectorParameters collectorParameters = newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(resolvedObjectType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getCoercedVariables().toMap())
                .graphQLContext(executionContext.getGraphQLContext())
                .build();

        MergedSelectionSet subFields = fieldCollector.collectFields(
                collectorParameters,
                parameters.getField(),
                Optional.ofNullable(executionContext.getGraphQLContext())
                        .map(graphqlContext -> graphqlContext.getBoolean(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT))
                        .orElse(false)
        );

        ExecutionStepInfo newExecutionStepInfo = executionStepInfo.changeTypeWithPreservedNonNull(resolvedObjectType);
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, newExecutionStepInfo);

        ExecutionStrategyParameters newParameters = parameters.transform(builder ->
                builder.executionStepInfo(newExecutionStepInfo)
                        .fields(subFields)
                        .nonNullFieldValidator(nonNullableFieldValidator)
                        .source(result)
        );

        // Calling this from the executionContext to ensure we shift back from mutation strategy to the query strategy.
        return executionContext.getQueryStrategy().executeObject(executionContext, newParameters);
    }

    @SuppressWarnings("SameReturnValue")
    private Object handleCoercionProblem(ExecutionContext context, ExecutionStrategyParameters parameters, CoercingSerializeException e) {
        SerializationError error = new SerializationError(parameters.getPath(), e);

        addErrorToRightContext(error, parameters, context);

        return null;
    }

    protected GraphQLObjectType resolveType(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLType fieldType) {
        // we can avoid a method call and type resolver environment allocation if we know it's an object type
        if (fieldType instanceof GraphQLObjectType) {
            return (GraphQLObjectType) fieldType;
        }
        return resolvedType.resolveType(executionContext, parameters.getField(), parameters.getSource(), parameters.getExecutionStepInfo(), fieldType, parameters.getLocalContext());
    }

    protected Iterable<Object> toIterable(ExecutionContext context, ExecutionStrategyParameters parameters, Object result) {
        if (FpKit.isIterable(result)) {
            return FpKit.toIterable(result);
        }

        handleTypeMismatchProblem(context, parameters);
        return null;
    }

    private void handleTypeMismatchProblem(ExecutionContext context, ExecutionStrategyParameters parameters) {
        TypeMismatchError error = new TypeMismatchError(parameters.getPath(), parameters.getExecutionStepInfo().getUnwrappedNonNullType());

        addErrorToRightContext(error, parameters, context);
    }

    /**
     * This has a side effect of incrementing the number of nodes returned and also checks
     * if max nodes were exceeded for this request.
     *
     * @param executionContext the execution context in play
     * @return true if max nodes were exceeded
     */
    private boolean incrementAndCheckMaxNodesExceeded(ExecutionContext executionContext) {
        int resultNodesCount = executionContext.getResultNodesInfo().incrementAndGetResultNodesCount();
        Integer maxNodes;
        if ((maxNodes = executionContext.getGraphQLContext().get(MAX_RESULT_NODES)) != null) {
            if (resultNodesCount > maxNodes) {
                executionContext.getResultNodesInfo().maxResultNodesExceeded();
                return true;
            }
        }
        return false;
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
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();
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
        return Introspection.getFieldDefinition(schema, parentType, field.getName());
    }

    /**
     * See (<a href="https://spec.graphql.org/October2021/#sec-Errors-and-Non-Nullability">...</a>),
     * <p>
     * If a non nullable child field type actually resolves to a null value and the parent type is nullable
     * then the parent must in fact become null
     * so we use exceptions to indicate this special case.  However if the parent is in fact a non nullable type
     * itself then we need to bubble that upwards again until we get to the root in which case the result
     * is meant to be null.
     *
     * @param e this indicates that a null value was returned for a non null field, which needs to cause the parent field
     *          to become null OR continue on as an exception
     * @throws NonNullableFieldWasNullException if a non null field resolves to a null value
     */
    protected void assertNonNullFieldPrecondition(NonNullableFieldWasNullException e) throws NonNullableFieldWasNullException {
        ExecutionStepInfo executionStepInfo = e.getExecutionStepInfo();
        if (executionStepInfo.hasParent() && executionStepInfo.getParent().isNonNullType()) {
            throw new NonNullableFieldWasNullException(e);
        }
    }

    protected void assertNonNullFieldPrecondition(NonNullableFieldWasNullException e, CompletableFuture<?> completableFuture) throws NonNullableFieldWasNullException {
        ExecutionStepInfo executionStepInfo = e.getExecutionStepInfo();
        if (executionStepInfo.hasParent() && executionStepInfo.getParent().isNonNullType()) {
            completableFuture.completeExceptionally(new NonNullableFieldWasNullException(e));
        }
    }

    protected ExecutionResult handleNonNullException(ExecutionContext executionContext, CompletableFuture<ExecutionResult> result, Throwable e) {
        ExecutionResult executionResult = null;
        List<GraphQLError> errors = ImmutableList.copyOf(executionContext.getErrors());
        Throwable underlyingException = e;
        if (e instanceof CompletionException) {
            underlyingException = e.getCause();
        }
        if (underlyingException instanceof NonNullableFieldWasNullException) {
            assertNonNullFieldPrecondition((NonNullableFieldWasNullException) underlyingException, result);
            if (!result.isDone()) {
                executionResult = new ExecutionResultImpl(null, errors);
                result.complete(executionResult);
            }
        } else if (underlyingException instanceof AbortExecutionException) {
            AbortExecutionException abortException = (AbortExecutionException) underlyingException;
            executionResult = abortException.toExecutionResult();
            result.complete(executionResult);
        } else {
            result.completeExceptionally(e);
        }
        return executionResult;
    }

    /**
     * Builds the type info hierarchy for the current field
     *
     * @param executionContext the execution context  in play
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param fieldDefinition  the field definition to build type info for
     * @param fieldContainer   the field container
     *
     * @return a new type info
     */
    protected ExecutionStepInfo createExecutionStepInfo(ExecutionContext executionContext,
                                                        ExecutionStrategyParameters parameters,
                                                        GraphQLFieldDefinition fieldDefinition,
                                                        GraphQLObjectType fieldContainer) {
        MergedField field = parameters.getField();
        ExecutionStepInfo parentStepInfo = parameters.getExecutionStepInfo();
        GraphQLOutputType fieldType = fieldDefinition.getType();
        List<GraphQLArgument> fieldArgDefs = fieldDefinition.getArguments();
        Supplier<ImmutableMapWithNullValues<String, Object>> argumentValues = ImmutableMapWithNullValues::emptyMap;
        //
        // no need to create args at all if there are none on the field def
        //
        if (!fieldArgDefs.isEmpty()) {
            argumentValues = getArgumentValues(executionContext, fieldArgDefs, field.getArguments());
        }


        return newExecutionStepInfo()
                .type(fieldType)
                .fieldDefinition(fieldDefinition)
                .fieldContainer(fieldContainer)
                .field(field)
                .path(parameters.getPath())
                .parentInfo(parentStepInfo)
                .arguments(argumentValues)
                .build();
    }

    @NotNull
    private static Supplier<ImmutableMapWithNullValues<String, Object>> getArgumentValues(ExecutionContext executionContext,
                                                                                          List<GraphQLArgument> fieldArgDefs,
                                                                                          List<Argument> fieldArgs) {
        Supplier<ImmutableMapWithNullValues<String, Object>> argumentValues;
        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        Supplier<ImmutableMapWithNullValues<String, Object>> argValuesSupplier = () -> {
            Map<String, Object> resolvedValues = ValuesResolver.getArgumentValues(codeRegistry,
                    fieldArgDefs,
                    fieldArgs,
                    executionContext.getCoercedVariables(),
                    executionContext.getGraphQLContext(),
                    executionContext.getLocale());

            return ImmutableMapWithNullValues.copyOf(resolvedValues);
        };
        argumentValues = FpKit.intraThreadMemoize(argValuesSupplier);
        return argumentValues;
    }

    // Errors that result from the execution of deferred fields are kept in the deferred context only.
    private static void addErrorToRightContext(GraphQLError error, ExecutionStrategyParameters parameters, ExecutionContext executionContext) {
        if (parameters.getDeferredCallContext() != null) {
            parameters.getDeferredCallContext().addError(error);
        } else {
            executionContext.addError(error);
        }
    }

    private static void addErrorsToRightContext(List<GraphQLError> errors, ExecutionStrategyParameters parameters, ExecutionContext executionContext) {
        if (parameters.getDeferredCallContext() != null) {
            parameters.getDeferredCallContext().addErrors(errors);
        } else {
            executionContext.addErrors(errors);
        }
    }


}
