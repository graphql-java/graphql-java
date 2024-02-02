package graphql.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.incremental.DeferredCall;
import graphql.execution.incremental.DeferredCallContext;
import graphql.execution.incremental.IncrementalCall;
import graphql.execution.incremental.DeferExecution;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.incremental.IncrementalPayload;
import graphql.util.FpKit;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static graphql.execution.MergedSelectionSet.newMergedSelectionSet;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
@PublicApi
public class AsyncExecutionStrategy extends AbstractAsyncExecutionStrategy {

    /**
     * The standard graphql execution strategy that runs fields asynchronously
     */
    public AsyncExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates a execution strategy that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use
     */
    public AsyncExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = ExecutionStrategyInstrumentationContext.nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters, executionContext.getInstrumentationState()));

        MergedSelectionSet fields = parameters.getFields();
        List<String> fieldNames = fields.getKeys();

        DeferExecutionSupport deferExecutionSupport = executionContext.isIncrementalSupport() ?
                new DeferExecutionSupport.DeferExecutionSupportImpl(
                    fields,
                    parameters,
                    executionContext,
                    this::resolveFieldWithInfo
                ) : DeferExecutionSupport.NOOP;

        executionContext.getIncrementalContext().enqueue(deferExecutionSupport.createCalls());

        // Only non-deferred fields should be considered for calculating the expected size of futures.
        Async.CombinedBuilder<FieldValueInfo> futures = Async
                .ofExpectedSize(fields.size() - deferExecutionSupport.deferredFieldsCount());

        for (String fieldName : fieldNames) {
            MergedField currentField = fields.getSubField(fieldName);

            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));

            CompletableFuture<FieldValueInfo> future;

            if (deferExecutionSupport.isDeferredField(currentField)) {
                executionStrategyCtx.onDeferredField(currentField);
            } else {
                future = resolveFieldWithInfo(executionContext, newParameters);
                futures.add(future);
            }
        }
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        futures.await().whenComplete((completeValueInfos, throwable) -> {
            List<String> fieldsExecutedOnInitialResult = deferExecutionSupport.getNonDeferredFieldNames(fieldNames);

            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, fieldsExecutedOnInitialResult, overallResult);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }

            Async.CombinedBuilder<ExecutionResult> executionResultFutures = Async.ofExpectedSize(completeValueInfos.size());
            for (FieldValueInfo completeValueInfo : completeValueInfos) {
                executionResultFutures.add(completeValueInfo.getFieldValue());
            }
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
            executionResultFutures.await().whenComplete(handleResultsConsumer);
        }).exceptionally((ex) -> {
            // if there are any issues with combining/handling the field results,
            // complete the future at all costs and bubble up any thrown exception so
            // the execution does not hang.
            executionStrategyCtx.onFieldValuesException();
            overallResult.completeExceptionally(ex);
            return null;
        });

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

    /**
     * The purpose of this class hierarchy is to encapsulate most of the logic for deferring field execution, thus
     * keeping the main execution strategy code clean and focused on the main execution logic.
     * <p>
     * The {@link NoOp} instance should be used when incremental support is not enabled for the current execution. The
     * methods in this class will return empty or no-op results, that should not impact the main execution.
     * <p>
     * {@link DeferExecutionSupportImpl} is the actual implementation that will be used when incremental support is enabled.
     */
    private interface DeferExecutionSupport {

        boolean isDeferredField(MergedField mergedField);

        int deferredFieldsCount();

        List<String> getNonDeferredFieldNames(List<String> allFieldNames);

        Set<IncrementalCall<? extends IncrementalPayload>> createCalls();

        DeferExecutionSupport NOOP = new DeferExecutionSupport.NoOp();

        /**
         * An implementation that actually executes the deferred fields.
         */
        class DeferExecutionSupportImpl implements DeferExecutionSupport {
            private final ImmutableListMultimap<DeferExecution, MergedField> deferExecutionToFields;
            private final ImmutableSet<MergedField> deferredFields;
            private final ImmutableList<String> nonDeferredFieldNames;
            private final ExecutionStrategyParameters parameters;
            private final ExecutionContext executionContext;
            private final BiFunction<ExecutionContext, ExecutionStrategyParameters, CompletableFuture<FieldValueInfo>> resolveFieldWithInfoFn;
            private final Map<String, Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>> dfCache = new HashMap<>();

            private DeferExecutionSupportImpl(
                    MergedSelectionSet mergedSelectionSet,
                    ExecutionStrategyParameters parameters,
                    ExecutionContext executionContext,
                    BiFunction<ExecutionContext, ExecutionStrategyParameters, CompletableFuture<FieldValueInfo>> resolveFieldWithInfoFn
            ) {
                this.executionContext = executionContext;
                this.resolveFieldWithInfoFn = resolveFieldWithInfoFn;
                ImmutableListMultimap.Builder<DeferExecution, MergedField> deferExecutionToFieldsBuilder = ImmutableListMultimap.builder();
                ImmutableSet.Builder<MergedField> deferredFieldsBuilder = ImmutableSet.builder();
                ImmutableList.Builder<String> nonDeferredFieldNamesBuilder = ImmutableList.builder();

                mergedSelectionSet.getSubFields().values().forEach(mergedField -> {
                    mergedField.getDeferExecutions().forEach(de -> {
                        deferExecutionToFieldsBuilder.put(de, mergedField);
                        deferredFieldsBuilder.add(mergedField);
                    });

                    if (mergedField.getDeferExecutions().isEmpty()) {
                        nonDeferredFieldNamesBuilder.add(mergedField.getSingleField().getResultKey());
                    }
                });

                this.deferExecutionToFields = deferExecutionToFieldsBuilder.build();
                this.deferredFields = deferredFieldsBuilder.build();
                this.parameters = parameters;
                this.nonDeferredFieldNames = nonDeferredFieldNamesBuilder.build();
            }

            @Override
            public boolean isDeferredField(MergedField mergedField) {
                return deferredFields.contains(mergedField);
            }

            @Override
            public int deferredFieldsCount() {
                return deferredFields.size();
            }

            @Override
            public List<String> getNonDeferredFieldNames(List<String> allFieldNames) {
                return this.nonDeferredFieldNames;
            }
            @Override
            public Set<IncrementalCall<? extends IncrementalPayload>> createCalls() {
                return deferExecutionToFields.keySet().stream()
                        .map(this::createDeferredCall)
                        .collect(Collectors.toSet());
            }

            private DeferredCall createDeferredCall(DeferExecution deferExecution) {
                DeferredCallContext deferredCallContext = new DeferredCallContext();

                List<MergedField> mergedFields = deferExecutionToFields.get(deferExecution);

                List<Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>> calls = mergedFields.stream()
                        .map(currentField -> this.createResultSupplier(currentField, deferredCallContext))
                        .collect(Collectors.toList());

                return new DeferredCall(
                        deferExecution.getLabel(),
                        this.parameters.getPath(),
                        calls,
                        deferredCallContext
                );
            }

            private Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>> createResultSupplier(
                    MergedField currentField,
                    DeferredCallContext deferredCallContext
            ) {
                Map<String, MergedField> fields = new LinkedHashMap<>();
                fields.put(currentField.getName(), currentField);

                ExecutionStrategyParameters callParameters = parameters.transform(builder ->
                        {
                            MergedSelectionSet mergedSelectionSet = newMergedSelectionSet().subFields(fields).build();
                            builder.deferredCallContext(deferredCallContext)
                                    .field(currentField)
                                    .fields(mergedSelectionSet)
                                    .path(parameters.getPath().segment(currentField.getName()))
                                    .parent(null); // this is a break in the parent -> child chain - it's a new start effectively
                        }
                );


                Instrumentation instrumentation = executionContext.getInstrumentation();
                InstrumentationContext<ExecutionResult> fieldCtx = instrumentation.beginDeferredField(
                        new InstrumentationDeferredFieldParameters(executionContext, callParameters), executionContext.getInstrumentationState()
                );

                return dfCache.computeIfAbsent(
                        currentField.getName(),
                        // The same field can be associated with multiple defer executions, so
                        // we memoize the field resolution to avoid multiple calls to the same data fetcher
                        key -> FpKit.interThreadMemoize(() -> {
                                    CompletableFuture<FieldValueInfo> fieldValueResult = resolveFieldWithInfoFn
                                            .apply(executionContext, callParameters);

                                    // Create a reference to the CompletableFuture that resolves an ExecutionResult
                                    // so we can pass it to the Instrumentation "onDispatched" callback.
                                    CompletableFuture<ExecutionResult> executionResultCF = fieldValueResult
                                            .thenCompose(FieldValueInfo::getFieldValue);

                                    fieldCtx.onDispatched(executionResultCF);

                                    return executionResultCF
                                            .thenApply(executionResult ->
                                                    new DeferredCall.FieldWithExecutionResult(currentField.getName(), executionResult)
                                            )
                                            .whenComplete((fieldWithExecutionResult, throwable) ->
                                                    fieldCtx.onCompleted(fieldWithExecutionResult.getExecutionResult(), throwable)
                                            );
                                }
                        )
                );
            }
        }

        /**
         * A no-op implementation that should be used when incremental support is not enabled for the current execution.
         */
        class NoOp implements DeferExecutionSupport {

            @Override
            public boolean isDeferredField(MergedField mergedField) {
                return false;
            }

            @Override
            public int deferredFieldsCount() {
                return 0;
            }

            @Override
            public List<String> getNonDeferredFieldNames(List<String> allFieldNames) {
                return allFieldNames;
            }

            @Override
            public Set<IncrementalCall<? extends IncrementalPayload>> createCalls() {
                return Collections.emptySet();
            }
        }
    }


}
