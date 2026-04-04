package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.DuckTyped;
import graphql.EngineRunningState;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.PublicSpi;
import graphql.SerializationError;
import graphql.TrivialDataFetcher;
import graphql.TypeMismatchError;
import graphql.UnresolvedTypeError;
import graphql.execution.directives.QueryDirectives;
import graphql.execution.directives.QueryDirectivesImpl;
import graphql.execution.incremental.DeferredExecutionSupport;
import graphql.execution.incremental.IncrementalExecutionContextKeys;
import graphql.execution.instrumentation.ExecuteObjectInstrumentationContext;
import graphql.execution.instrumentation.FieldFetchingInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.reactive.ReactiveSupport;
import graphql.extensions.ExtensionsBuilder;
import graphql.introspection.Introspection;
import graphql.language.Field;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.LightDataFetcher;
import graphql.util.FpKit;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static graphql.execution.Async.exceptionallyCompletedFuture;
import static graphql.execution.FieldValueInfo.CompleteValueType.ENUM;
import static graphql.execution.FieldValueInfo.CompleteValueType.LIST;
import static graphql.execution.FieldValueInfo.CompleteValueType.NULL;
import static graphql.execution.FieldValueInfo.CompleteValueType.OBJECT;
import static graphql.execution.FieldValueInfo.CompleteValueType.SCALAR;
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
 * The second phase (value completion) is handled by the methods {@link #completeField(ExecutionContext, ExecutionStrategyParameters, Object)}
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
     * Returns true if the instrumentation is guaranteed to be a no-op for per-field callbacks
     * (beginFieldExecution, beginFieldFetching, beginFieldCompletion, beginFieldListCompletion, instrumentDataFetcher).
     * When true, the execution engine skips allocating instrumentation parameter objects in the hot path.
     */
    static boolean isNoOpFieldInstrumentation(Instrumentation instrumentation) {
        return instrumentation.getClass() == SimplePerformantInstrumentation.class;
    }


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
        return mergedField.getResultKey();
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
        executionContext.throwIfCancelled();

        boolean noOpInstr = executionContext.isNoOpFieldInstrumentation();
        boolean noOpDL = executionContext.isNoOpDataLoaderDispatch();

        ExecuteObjectInstrumentationContext resolveObjectCtx;
        if (noOpInstr) {
            resolveObjectCtx = ExecuteObjectInstrumentationContext.NOOP;
        } else {
            Instrumentation instrumentation = executionContext.getInstrumentation();
            InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);
            resolveObjectCtx = ExecuteObjectInstrumentationContext.nonNullCtx(
                    instrumentation.beginExecuteObject(instrumentationParameters, executionContext.getInstrumentationState())
            );
        }

        List<String> fieldNames = parameters.getFields().getKeys();

        // Inline the incremental support check to avoid createDeferredExecutionSupport method call
        // and NOOP.getNonDeferredFieldNames virtual dispatch when incremental support is disabled (~5.5K/op)
        boolean hasIncrementalSupport = executionContext.hasIncrementalSupport();
        DeferredExecutionSupport deferredExecutionSupport;
        List<String> fieldsExecutedOnInitialResult;
        if (hasIncrementalSupport) {
            deferredExecutionSupport = createDeferredExecutionSupport(executionContext, parameters);
            fieldsExecutedOnInitialResult = deferredExecutionSupport.getNonDeferredFieldNames(fieldNames);
        } else {
            deferredExecutionSupport = DeferredExecutionSupport.NOOP;
            fieldsExecutedOnInitialResult = fieldNames;
        }
        if (!noOpDL) {
            executionContext.getDataLoaderDispatcherStrategy().executeObject(executionContext, parameters, fieldsExecutedOnInitialResult.size());
        }
        Object resolvedFieldResult = getAsyncFieldValueInfo(executionContext, parameters, deferredExecutionSupport);

        if (!noOpInstr) {
            resolveObjectCtx.onDispatched();
        }

        // getAsyncFieldValueInfo returns either a List<FieldValueInfo> (materialized) or a CombinedBuilder
        Object fieldValueInfosResult;
        if (resolvedFieldResult instanceof List) {
            fieldValueInfosResult = resolvedFieldResult;
        } else {
            @SuppressWarnings("unchecked")
            Async.CombinedBuilder<FieldValueInfo> builder = (Async.CombinedBuilder<FieldValueInfo>) resolvedFieldResult;
            fieldValueInfosResult = builder.awaitPolymorphic();
        }
        if (fieldValueInfosResult instanceof CompletableFuture) {
            CompletableFuture<Map<String, Object>> overallResult = new CompletableFuture<>();
            BiConsumer<List<Object>, Throwable> handleResultsConsumer = buildFieldValueMap(fieldsExecutedOnInitialResult, overallResult, executionContext);
            CompletableFuture<List<FieldValueInfo>> fieldValueInfos = (CompletableFuture<List<FieldValueInfo>>) fieldValueInfosResult;
            fieldValueInfos.whenComplete((completeValueInfos, throwable) -> {
                throwable = executionContext.possibleCancellation(throwable);

                if (throwable != null) {
                    handleResultsConsumer.accept(null, throwable);
                    return;
                }

                Async.CombinedBuilder<Object> resultFutures = fieldValuesCombinedBuilder(completeValueInfos);
                if (!noOpDL) {
                    executionContext.getDataLoaderDispatcherStrategy().executeObjectOnFieldValuesInfo(completeValueInfos, parameters);
                }
                resolveObjectCtx.onFieldValuesInfo(completeValueInfos);
                resultFutures.await().whenComplete(handleResultsConsumer);
            }).exceptionally((ex) -> {
                // if there are any issues with combining/handling the field results,
                // complete the future at all costs and bubble up any thrown exception so
                // the execution does not hang.
                if (!noOpDL) {
                    executionContext.getDataLoaderDispatcherStrategy().executeObjectOnFieldValuesException(ex, parameters);
                }
                resolveObjectCtx.onFieldValuesException();
                overallResult.completeExceptionally(ex);
                return null;
            });
            overallResult.whenComplete(resolveObjectCtx::onCompleted);
            return overallResult;
        } else {
            List<FieldValueInfo> completeValueInfos = (List<FieldValueInfo>) fieldValueInfosResult;

            if (!noOpDL) {
                executionContext.getDataLoaderDispatcherStrategy().executeObjectOnFieldValuesInfo(completeValueInfos, parameters);
            }
            if (!noOpInstr) {
                resolveObjectCtx.onFieldValuesInfo(completeValueInfos);
            }

            // Fast path: extract field values directly and check for CFs in a single pass,
            // avoiding CombinedBuilder (Many object + Object[] array) allocation overhead
            int size = completeValueInfos.size();
            Object[] fieldValues = new Object[size];
            boolean hasCF = false;
            for (int i = 0; i < size; i++) {
                Object val = completeValueInfos.get(i).getFieldValueObject();
                fieldValues[i] = val;
                if (val instanceof CompletableFuture) {
                    hasCF = true;
                }
            }

            if (hasCF) {
                Async.CombinedBuilder<Object> resultFutures = Async.ofExpectedSize(size);
                for (Object val : fieldValues) {
                    resultFutures.addObject(val);
                }
                CompletableFuture<Map<String, Object>> overallResult = new CompletableFuture<>();
                BiConsumer<List<Object>, Throwable> handleResultsConsumer = buildFieldValueMap(fieldsExecutedOnInitialResult, overallResult, executionContext);
                resultFutures.await().whenComplete(handleResultsConsumer);
                overallResult.whenComplete(resolveObjectCtx::onCompleted);
                return overallResult;
            } else {
                @SuppressWarnings("unchecked")
                List<Object> results = (List<Object>) (List<?>) Arrays.asList(fieldValues);
                Map<String, Object> fieldValueMap = executionContext.getResponseMapFactory().createInsertionOrdered(fieldsExecutedOnInitialResult, results);
                resolveObjectCtx.onCompleted(fieldValueMap, null);
                return fieldValueMap;
            }
        }
    }

    private static Async.@NonNull CombinedBuilder<Object> fieldValuesCombinedBuilder(List<FieldValueInfo> completeValueInfos) {
        Async.CombinedBuilder<Object> resultFutures = Async.ofExpectedSize(completeValueInfos.size());
        for (FieldValueInfo completeValueInfo : completeValueInfos) {
            resultFutures.addObject(completeValueInfo.getFieldValueObject());
        }
        return resultFutures;
    }

    private BiConsumer<List<Object>, Throwable> buildFieldValueMap(List<String> fieldNames, CompletableFuture<Map<String, Object>> overallResult, ExecutionContext executionContext) {
        return (List<Object> results, Throwable exception) -> {
            exception = executionContext.possibleCancellation(exception);

            if (exception != null) {
                handleValueException(overallResult, exception, executionContext);
                return;
            }
            Map<String, Object> resolvedValuesByField = executionContext.getResponseMapFactory().createInsertionOrdered(fieldNames, results);
            overallResult.complete(resolvedValuesByField);
        };
    }

    DeferredExecutionSupport createDeferredExecutionSupport(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        MergedSelectionSet fields = parameters.getFields();

        return executionContext.hasIncrementalSupport() ?
                new DeferredExecutionSupport.DeferredExecutionSupportImpl(
                        fields,
                        parameters,
                        executionContext,
                        (ec, esp) -> Async.toCompletableFuture(resolveFieldWithInfo(ec, esp)),
                        this::createExecutionStepInfo
                ) : DeferredExecutionSupport.NOOP;

    }

    /**
     * Returns either a {@code List<FieldValueInfo>} (when all fields resolved synchronously) or
     * an {@code Async.CombinedBuilder<FieldValueInfo>} (when at least one field resolved asynchronously).
     * Callers must check the return type and call {@code awaitPolymorphic()} only on CombinedBuilder results.
     */
    @DuckTyped(shape = "List<FieldValueInfo> | Async.CombinedBuilder<FieldValueInfo>")
    Object getAsyncFieldValueInfo(
            ExecutionContext executionContext,
            ExecutionStrategyParameters parameters,
            DeferredExecutionSupport deferredExecutionSupport
    ) {
        executionContext.throwIfCancelled();

        MergedSelectionSet fields = parameters.getFields();
        boolean hasIncrementalSupport = executionContext.hasIncrementalSupport();

        if (hasIncrementalSupport) {
            executionContext.getIncrementalCallState().enqueue(deferredExecutionSupport.createCalls());
        }

        // Only non-deferred fields should be considered for calculating the expected size of futures.
        int expectedSize = hasIncrementalSupport
                ? fields.size() - deferredExecutionSupport.deferredFieldsCount()
                : fields.size();

        // Collect field results into an array, tracking whether any are CompletableFutures.
        // This avoids allocating a CombinedBuilder (Many object + internal Object[]) for the
        // common case where all fields resolve synchronously.
        Object[] items = new Object[expectedSize];
        int ix = 0;
        boolean hasCF = false;

        // Iterate over values directly instead of keys+getSubField(key) to avoid one
        // ImmutableMap.get() hash lookup per field (~66K lookups per benchmark op eliminated)
        for (MergedField currentField : fields.getSubFields().values()) {
            executionContext.throwIfCancelled();

            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters.transform(currentField, fieldPath, parameters);

            // Skip the per-field isDeferredField virtual call when incremental support is disabled
            if (!hasIncrementalSupport || !deferredExecutionSupport.isDeferredField(currentField)) {
                Object fieldValueInfo = resolveFieldWithInfo(executionContext, newParameters);
                items[ix++] = fieldValueInfo;
                if (fieldValueInfo instanceof CompletableFuture) {
                    hasCF = true;
                }
            }

        }

        if (hasIncrementalSupport
                && deferredExecutionSupport.deferredFieldsCount() > 0
                && executionContext.getGraphQLContext().getBoolean(IncrementalExecutionContextKeys.ENABLE_EAGER_DEFER_START, false)) {

            executionContext.getIncrementalCallState().startDrainingNow();
        }

        if (!hasCF) {
            // All fields resolved synchronously — return the materialized list directly,
            // avoiding CombinedBuilder (Many object) allocation
            @SuppressWarnings("unchecked")
            List<FieldValueInfo> result = (List<FieldValueInfo>) (List<?>) Arrays.asList(items);
            return result;
        } else {
            // At least one field is async — build a CombinedBuilder from the collected items
            Async.CombinedBuilder<FieldValueInfo> futures = Async.ofExpectedSize(expectedSize);
            for (Object item : items) {
                futures.addObject(item);
            }
            return futures;
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
        // Look up field definition + data fetcher from per-execution cache to avoid
        // per-field getFieldDef (field visibility dispatch + map lookup) and getDataFetcher
        // (2-3 HashMap lookups + resolveDataFetcher) for repeated field resolutions on the same type.
        GraphQLObjectType parentType = parameters.getExecutionStepInfo().getUnwrappedNonNullTypeAs();
        String fieldName = parameters.getField().getSingleField().getName();
        ExecutionContext.FieldResolveInfo resolveInfo = executionContext.getCachedFieldResolveInfo(parentType, fieldName);
        if (resolveInfo == null) {
            GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, parameters.getField().getSingleField());
            GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
            DataFetcher<?> dataFetcher = codeRegistry.getDataFetcher(parentType.getName(), fieldDef.getName(), fieldDef);
            resolveInfo = new ExecutionContext.FieldResolveInfo(fieldDef, dataFetcher);
            executionContext.putCachedFieldResolveInfo(parentType, fieldName, resolveInfo);
        }
        GraphQLFieldDefinition fieldDef = resolveInfo.fieldDef;

        boolean noOpFieldInstr = executionContext.isNoOpFieldInstrumentation();

        InstrumentationContext<Object> fieldCtx = null;
        if (!noOpFieldInstr) {
            Instrumentation instrumentation = executionContext.getInstrumentation();
            Supplier<ExecutionStepInfo> executionStepInfo = FpKit.intraThreadMemoize(() -> createExecutionStepInfo(executionContext, parameters, fieldDef, null));
            fieldCtx = nonNullCtx(instrumentation.beginFieldExecution(
                    new InstrumentationFieldParameters(executionContext, executionStepInfo), executionContext.getInstrumentationState()
            ));
        }

        // Pass the cached fieldDef and dataFetcher to avoid redundant lookups inside fetchField
        Object fetchedValueObj = fetchField(resolveInfo, executionContext, parameters);
        if (fetchedValueObj instanceof CompletableFuture) {
            CompletableFuture<Object> fetchFieldFuture = (CompletableFuture<Object>) fetchedValueObj;
            CompletableFuture<FieldValueInfo> result = fetchFieldFuture.thenApply((fetchedValue) -> {
                if (!executionContext.isNoOpDataLoaderDispatch()) {
                    executionContext.getDataLoaderDispatcherStrategy().startComplete(parameters);
                }
                FieldValueInfo completeFieldResult = completeField(fieldDef, executionContext, parameters, fetchedValue);
                if (!executionContext.isNoOpDataLoaderDispatch()) {
                    executionContext.getDataLoaderDispatcherStrategy().stopComplete(parameters);
                }
                return completeFieldResult;
            });

            if (fieldCtx != null) {
                fieldCtx.onDispatched();
                result.whenComplete(fieldCtx::onCompleted);
            }
            return result;
        } else {
            try {
                FieldValueInfo fieldValueInfo = completeField(fieldDef, executionContext, parameters, fetchedValueObj);
                if (fieldCtx != null) {
                    fieldCtx.onDispatched();
                    fieldCtx.onCompleted(FetchedValue.getFetchedValue(fetchedValueObj), null);
                }
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
     * in the query, hence the fieldList.  However, the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     *
     * @return a promise to a value object or the value itself.  The value maybe a raw object OR a {@link FetchedValue}
     *
     * @throws NonNullableFieldWasNullException in the future if a non-null field resolves to a null value
     */
    @DuckTyped(shape = "CompletableFuture<FetchedValue|Object> | <FetchedValue|Object>")
    protected Object fetchField(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        MergedField field = parameters.getField();
        GraphQLObjectType parentType = parameters.getExecutionStepInfo().getUnwrappedNonNullTypeAs();
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field.getSingleField());
        return fetchField(fieldDef, executionContext, parameters);
    }

    @DuckTyped(shape = "CompletableFuture<FetchedValue|Object> | <FetchedValue|Object>")
    private Object fetchField(ExecutionContext.FieldResolveInfo resolveInfo, ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        return fetchField(resolveInfo.fieldDef, resolveInfo.dataFetcher, executionContext, parameters);
    }

    @DuckTyped(shape = "CompletableFuture<FetchedValue|Object> | <FetchedValue|Object>")
    private Object fetchField(GraphQLFieldDefinition fieldDef, ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        GraphQLObjectType parentType = parameters.getExecutionStepInfo().getUnwrappedNonNullTypeAs();
        GraphQLCodeRegistry codeRegistry = executionContext.getGraphQLSchema().getCodeRegistry();
        DataFetcher<?> originalDataFetcher = codeRegistry.getDataFetcher(parentType.getName(), fieldDef.getName(), fieldDef);
        return fetchField(fieldDef, originalDataFetcher, executionContext, parameters);
    }

    @DuckTyped(shape = "CompletableFuture<FetchedValue|Object> | <FetchedValue|Object>")
    private Object fetchField(GraphQLFieldDefinition fieldDef, DataFetcher<?> originalDataFetcher, ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        executionContext.throwIfCancelled();

        if (incrementAndCheckMaxNodesExceeded(executionContext)) {
            return null;
        }

        boolean noOpFieldInstr = executionContext.isNoOpFieldInstrumentation();

        // if the DF (like PropertyDataFetcher) does not use the arguments or execution step info then dont build any
        // For no-op instrumentation, skip the intraThreadMemoize wrapper since the supplier is called at most once
        // in the normal code path, avoiding an IntraThreadMemoizedSupplier allocation per field.
        // parentType and field are computed lazily inside the lambda to reduce capture count from 6 to 4 fields,
        // saving 16 bytes per lambda and avoiding ~54K getUnwrappedNonNullTypeAs()+getField() calls per op
        // for PropertyDataFetcher fields where the DFE supplier is never evaluated.
        Supplier<DataFetchingEnvironment> dfeFactory = () -> {
            GraphQLObjectType parentType = parameters.getExecutionStepInfo().getUnwrappedNonNullTypeAs();
            MergedField field = parameters.getField();

            Supplier<ExecutionStepInfo> executionStepInfo = FpKit.intraThreadMemoize(
                    () -> createExecutionStepInfo(executionContext, parameters, fieldDef, parentType));

            Supplier<Map<String, Object>> argumentValues = () -> executionStepInfo.get().getArguments();

            Supplier<ExecutableNormalizedField> normalizedFieldSupplier = getNormalizedField(executionContext, parameters, executionStepInfo);

            // DataFetchingFieldSelectionSet and QueryDirectives is a supplier of sorts - eg a lazy pattern
            DataFetchingFieldSelectionSet fieldCollector = DataFetchingFieldSelectionSetImpl.newCollector(executionContext.getGraphQLSchema(), fieldDef.getType(), normalizedFieldSupplier);
            QueryDirectives queryDirectives = new QueryDirectivesImpl(field,
                    executionContext.getGraphQLSchema(),
                    executionContext.getCoercedVariables(),
                    executionContext.getNormalizedVariables(),
                    executionContext.getGraphQLContext(),
                    executionContext.getLocale());


            return newDataFetchingEnvironment(executionContext)
                    .source(parameters.getSource())
                    .localContext(parameters.getLocalContext())
                    .arguments(argumentValues)
                    .fieldDefinition(fieldDef)
                    .mergedField(field)
                    .fieldType(fieldDef.getType())
                    .executionStepInfo(executionStepInfo)
                    .parentType(parentType)
                    .selectionSet(fieldCollector)
                    .queryDirectives(queryDirectives)
                    .deferredCallContext(parameters.getDeferredCallContext())
                    .level(parameters.getPath().getLevel())
                    .build();
        };
        Supplier<DataFetchingEnvironment> dataFetchingEnvironment = noOpFieldInstr ? dfeFactory : FpKit.intraThreadMemoize(dfeFactory);

        FieldFetchingInstrumentationContext fetchCtx;
        DataFetcher<?> dataFetcher;
        if (noOpFieldInstr) {
            fetchCtx = FieldFetchingInstrumentationContext.NOOP;
            dataFetcher = originalDataFetcher;
        } else {
            Instrumentation instrumentation = executionContext.getInstrumentation();
            InstrumentationFieldFetchParameters instrumentationFieldFetchParams = new InstrumentationFieldFetchParameters(executionContext, dataFetchingEnvironment, parameters, originalDataFetcher instanceof TrivialDataFetcher);
            fetchCtx = FieldFetchingInstrumentationContext.nonNullCtx(instrumentation.beginFieldFetching(instrumentationFieldFetchParams,
                    executionContext.getInstrumentationState())
            );
            dataFetcher = instrumentation.instrumentDataFetcher(originalDataFetcher, instrumentationFieldFetchParams, executionContext.getInstrumentationState());
        }

        Object fetchedObject = invokeDataFetcher(executionContext, parameters, fieldDef, dataFetchingEnvironment, originalDataFetcher, dataFetcher);
        if (!executionContext.isNoOpDataLoaderDispatch()) {
            executionContext.getDataLoaderDispatcherStrategy().fieldFetched(executionContext, parameters, dataFetcher, fetchedObject, dataFetchingEnvironment);
        }
        if (!noOpFieldInstr) {
            fetchCtx.onDispatched();
            fetchCtx.onFetchedValue(fetchedObject);
        }
        // if it's a subscription, leave any reactive objects alone
        if (!executionContext.isSubscriptionOperation()) {
            // possible convert reactive objects into CompletableFutures
            fetchedObject = ReactiveSupport.fetchedObject(fetchedObject);
        }
        if (fetchedObject instanceof CompletableFuture) {
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> fetchedValue = (CompletableFuture<Object>) fetchedObject;
            EngineRunningState engineRunningState = executionContext.getEngineRunningState();

            CompletableFuture<CompletableFuture<Object>> handleCF = engineRunningState.handle(fetchedValue, (result, exception) -> {
                // because we added an artificial CF, we need to unwrap the exception
                Throwable possibleWrappedException = engineRunningState.possibleCancellation(exception);

                if (possibleWrappedException != null) {
                    CompletableFuture<DataFetcherResult<Object>> handledExceptionResult = handleFetchingException(dataFetchingEnvironment.get(), parameters, possibleWrappedException);
                    return handledExceptionResult.thenApply( handledResult -> {
                        fetchCtx.onExceptionHandled(handledResult);
                        fetchCtx.onCompleted(result, exception);
                        return handledResult;
                    });
                } else {
                    fetchCtx.onCompleted(result, exception);
                    // we can simply return the fetched value CF and avoid a allocation
                    return fetchedValue;
                }
            });
            CompletableFuture<Object> rawResultCF = engineRunningState.compose(handleCF, Function.identity());
            return rawResultCF
                    .thenApply(result -> unboxPossibleDataFetcherResult(executionContext, parameters, result));
        } else {
            if (!noOpFieldInstr) {
                fetchCtx.onCompleted(fetchedObject, null);
            }
            return unboxPossibleDataFetcherResult(executionContext, parameters, fetchedObject);
        }
    }

    /*
     * ExecutionContext is not used in the method, but the java agent uses it, so it needs to be present
     */
    @SuppressWarnings("unused")
    private Object invokeDataFetcher(ExecutionContext executionContext, ExecutionStrategyParameters parameters, GraphQLFieldDefinition fieldDef, Supplier<DataFetchingEnvironment> dataFetchingEnvironment, DataFetcher<?> originalDataFetcher, DataFetcher<?> dataFetcher) {
        Object fetchedValue;
        try {
            Object fetchedValueRaw;
            if (dataFetcher instanceof LightDataFetcher) {
                fetchedValueRaw = ((LightDataFetcher<?>) dataFetcher).get(fieldDef, parameters.getSource(), dataFetchingEnvironment);
            } else {
                fetchedValueRaw = dataFetcher.get(dataFetchingEnvironment.get());
            }
            if (!executionContext.isNoOpProfiler()) {
                executionContext.getProfiler().fieldFetched(fetchedValueRaw, originalDataFetcher, dataFetcher, parameters.getPath(), fieldDef, parameters.getExecutionStepInfo().getType());
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

    /**
     * If the data fetching returned a {@link DataFetcherResult} then it can contain errors and new local context
     * and hence it gets turned into a {@link FetchedValue} but otherwise this method returns the unboxed
     * value without the wrapper.  This means its more efficient overall by default.
     *
     * @param executionContext the execution context in play
     * @param parameters       the parameters in play
     * @param result           the fetched raw object
     *
     * @return an unboxed value which can be a FetchedValue or an Object
     */
    @DuckTyped(shape = "FetchedValue | Object")
    protected Object unboxPossibleDataFetcherResult(ExecutionContext executionContext,
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
            return executionContext.getValueUnboxer().unbox(result);
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

    protected <T> CompletableFuture<DataFetcherResult<T>> handleFetchingException(
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

    private <T> CompletableFuture<DataFetcherResult<T>> asyncHandleException(DataFetcherExceptionHandler handler, DataFetcherExceptionHandlerParameters handlerParameters) {
        //noinspection unchecked
        return handler.handleException(handlerParameters).thenApply(
                handlerResult -> (DataFetcherResult<T>) DataFetcherResult.newResult().errors(handlerResult.getErrors()).build()
        );
    }

    /**
     * Called to complete a field based on the type of the field.
     * <p>
     * If the field is a scalar type, then it will be coerced  and returned.  However, if the field type is an complex object type, then
     * the execution strategy will be called recursively again to execute the fields of that type before returning.
     * <p>
     * Graphql fragments mean that for any give logical field can have one or more {@link Field} values associated with it
     * in the query, hence the fieldList.  However, the first entry is representative of the field for most purposes.
     *
     * @param executionContext contains the top level execution parameters
     * @param parameters       contains the parameters holding the fields to be executed and source object
     * @param fetchedValue     the fetched raw value or perhaps a {@link FetchedValue} wrapper of that value
     *
     * @return a {@link FieldValueInfo}
     *
     * @throws NonNullableFieldWasNullException in the {@link FieldValueInfo#getFieldValueFuture()} future
     *                                          if a nonnull field resolves to a null value
     */
    protected FieldValueInfo completeField(ExecutionContext executionContext,
                                           ExecutionStrategyParameters parameters,
                                           @DuckTyped(shape = "Object | FetchedValue")
                                           Object fetchedValue) {
        executionContext.throwIfCancelled();

        Field field = parameters.getField().getSingleField();
        GraphQLObjectType parentType = parameters.getExecutionStepInfo().getUnwrappedNonNullTypeAs();
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field);
        return completeField(fieldDef, executionContext, parameters, fetchedValue);
    }

    private FieldValueInfo completeField(GraphQLFieldDefinition fieldDef, ExecutionContext executionContext, ExecutionStrategyParameters parameters, Object fetchedValue) {
        GraphQLObjectType parentType = parameters.getExecutionStepInfo().getUnwrappedNonNullTypeAs();
        ExecutionStepInfo executionStepInfo = createExecutionStepInfo(executionContext, parameters, fieldDef, parentType);

        boolean noOpFieldInstr = executionContext.isNoOpFieldInstrumentation();

        InstrumentationContext<Object> ctxCompleteField = null;
        if (!noOpFieldInstr) {
            Instrumentation instrumentation = executionContext.getInstrumentation();
            InstrumentationFieldCompleteParameters instrumentationParams = new InstrumentationFieldCompleteParameters(executionContext, parameters, () -> executionStepInfo, fetchedValue);
            ctxCompleteField = nonNullCtx(instrumentation.beginFieldCompletion(
                    instrumentationParams, executionContext.getInstrumentationState()
            ));
        }

        ExecutionStrategyParameters newParameters = parameters.transform(
                executionStepInfo,
                FetchedValue.getLocalContext(fetchedValue, parameters.getLocalContext()),
                FetchedValue.getFetchedValue(fetchedValue)
        );

        FieldValueInfo fieldValueInfo = completeValue(executionContext, newParameters);
        if (ctxCompleteField != null) {
            ctxCompleteField.onDispatched();
            if (fieldValueInfo.isFutureValue()) {
                CompletableFuture<Object> executionResultFuture = fieldValueInfo.getFieldValueFuture();
                executionResultFuture.whenComplete(ctxCompleteField::onCompleted);
            } else {
                ctxCompleteField.onCompleted(fieldValueInfo.getFieldValueObject(), null);
            }
        }
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

        boolean noOpFieldInstr = executionContext.isNoOpFieldInstrumentation();

        InstrumentationContext<Object> completeListCtx = null;
        if (!noOpFieldInstr) {
            Instrumentation instrumentation = executionContext.getInstrumentation();
            InstrumentationFieldCompleteParameters instrumentationParams = new InstrumentationFieldCompleteParameters(executionContext, parameters, () -> executionStepInfo, iterableValues);
            completeListCtx = nonNullCtx(instrumentation.beginFieldListCompletion(
                    instrumentationParams, executionContext.getInstrumentationState()
            ));
        }

        List<FieldValueInfo> fieldValueInfos = new ArrayList<>(size.orElse(1));
        int index = 0;
        for (Object item : iterableValues) {
            if (incrementAndCheckMaxNodesExceeded(executionContext)) {
                return new FieldValueInfo(NULL, null, fieldValueInfos);
            }

            ResultPath indexedPath = parameters.getPath().segment(index);

            ExecutionStepInfo stepInfoForListElement = executionStepInfoFactory.newExecutionStepInfoForListElement(executionStepInfo, indexedPath);

            Object fetchedValue = unboxPossibleDataFetcherResult(executionContext, parameters, item);

            ExecutionStrategyParameters newParameters = parameters.transform(
                    stepInfoForListElement,
                    indexedPath,
                    FetchedValue.getLocalContext(fetchedValue, parameters.getLocalContext()),
                    FetchedValue.getFetchedValue(fetchedValue)
            );

            fieldValueInfos.add(completeValue(executionContext, newParameters));
            index++;
        }

        // Fast path: extract field values directly and check for CFs in a single pass,
        // avoiding CombinedBuilder (Many object + Object[] array) allocation overhead
        int listSize = fieldValueInfos.size();
        Object listOrPromiseToList;
        if (listSize == 0) {
            if (completeListCtx != null) {
                completeListCtx.onCompleted(Collections.emptyList(), null);
            }
            listOrPromiseToList = Collections.emptyList();
        } else {
            Object[] values = new Object[listSize];
            boolean hasCF = false;
            for (int i = 0; i < listSize; i++) {
                Object val = fieldValueInfos.get(i).getFieldValueObject();
                values[i] = val;
                if (val instanceof CompletableFuture) {
                    hasCF = true;
                }
            }

            if (hasCF) {
                Async.CombinedBuilder<Object> resultFutures = Async.ofExpectedSize(listSize);
                for (Object val : values) {
                    resultFutures.addObject(val);
                }
                CompletableFuture<Object> overallResult = new CompletableFuture<>();
                if (completeListCtx != null) {
                    completeListCtx.onDispatched();
                    overallResult.whenComplete(completeListCtx::onCompleted);
                }
                resultFutures.await().whenComplete((results, exception) -> {
                    exception = executionContext.possibleCancellation(exception);

                    if (exception != null) {
                        handleValueException(overallResult, exception, executionContext);
                        return;
                    }
                    overallResult.complete(results);
                });
                listOrPromiseToList = overallResult;
            } else {
                @SuppressWarnings("unchecked")
                List<Object> results = (List<Object>) (List<?>) Arrays.asList(values);
                if (completeListCtx != null) {
                    completeListCtx.onCompleted(results, null);
                }
                listOrPromiseToList = results;
            }
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

        // Try the per-execution cache first — avoids FieldCollectorParameters allocation,
        // LinkedHashMap/LinkedHashSet allocation in collectFields, and MergedField creation per sub-field
        MergedField currentField = parameters.getField();
        MergedSelectionSet subFields = executionContext.getCachedCollectedFields(resolvedObjectType, currentField);
        if (subFields == null) {
            FieldCollectorParameters collectorParameters = new FieldCollectorParameters(
                    executionContext.getGraphQLSchema(),
                    resolvedObjectType,
                    executionContext.getFragmentsByName(),
                    executionContext.getCoercedVariables().toMap(),
                    executionContext.getGraphQLContext()
            );
            subFields = fieldCollector.collectFields(
                    collectorParameters,
                    currentField,
                    executionContext.hasIncrementalSupport()
            );
            executionContext.putCachedCollectedFields(resolvedObjectType, currentField, subFields);
        }

        ExecutionStepInfo newExecutionStepInfo = executionStepInfo.changeTypeWithPreservedNonNull(resolvedObjectType);

        ExecutionStrategyParameters newParameters = parameters.transform(newExecutionStepInfo,
                subFields,
                result);

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
     *
     * @return true if max nodes were exceeded
     */
    private boolean incrementAndCheckMaxNodesExceeded(ExecutionContext executionContext) {
        // Use cached maxResultNodes from ExecutionContext to avoid per-field ConcurrentHashMap.get() lookup.
        // When MAX_RESULT_NODES is not configured (maxNodes <= 0), skip the AtomicInteger.incrementAndGet()
        // entirely — this avoids ~66K+ CAS operations per benchmark op with their associated memory barriers
        // and cache line contention in multi-threaded execution.
        int maxNodes = executionContext.getMaxResultNodes();
        if (maxNodes <= 0) {
            return false;
        }
        int resultNodesCount = executionContext.getResultNodesInfo().incrementAndGetResultNodesCount();
        if (resultNodesCount > maxNodes) {
            executionContext.getResultNodesInfo().maxResultNodesExceeded();
            return true;
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
        GraphQLObjectType parentType = parameters.getExecutionStepInfo().getUnwrappedNonNullTypeAs();
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
     *
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
        return executionStepInfoFactory.createExecutionStepInfo(executionContext,
                parameters,
                fieldDefinition,
                fieldContainer);
    }

    private Supplier<ExecutionStepInfo> createExecutionStepInfo(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext, parameters, parameters.getField().getSingleField());
        return FpKit.intraThreadMemoize(() -> createExecutionStepInfo(executionContext, parameters, fieldDef, null));
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
