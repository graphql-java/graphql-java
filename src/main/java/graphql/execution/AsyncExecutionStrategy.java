package graphql.execution;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.defer.DeferredCall;
import graphql.execution.defer.DeferredErrorSupport;
import graphql.execution.incremental.DeferExecution;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.util.FpKit;

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

//        if(true /* check if incremental support is enabled*/) {
            SomethingDefer somethingDefer = new SomethingDefer(
                    fields,
                    parameters,
                    executionContext,
                    this::resolveFieldWithInfo
            );

            executionContext.getDeferSupport().enqueue(somethingDefer.createCalls());
//        }

        Async.CombinedBuilder<FieldValueInfo> futures = Async.ofExpectedSize(fields.size() - somethingDefer.deferredFields.size());

        for (String fieldName : fieldNames) {
            MergedField currentField = fields.getSubField(fieldName);

            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));

            CompletableFuture<FieldValueInfo> future;

            if (somethingDefer.isDeferredField(currentField)) {
                executionStrategyCtx.onDeferredField(currentField);
            } else {
                future = resolveFieldWithInfo(executionContext, newParameters);
                futures.add(future);
            }

        }
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        futures.await().whenComplete((completeValueInfos, throwable) -> {
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, fieldNames, overallResult);
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

    private static class SomethingDefer {
        private final ImmutableListMultimap<DeferExecution, MergedField> deferExecutionToFields;
        private final ImmutableSet<MergedField> deferredFields;
        private final ExecutionStrategyParameters parameters;
        private final ExecutionContext executionContext;
        private final BiFunction<ExecutionContext, ExecutionStrategyParameters, CompletableFuture<FieldValueInfo>> resolveFieldWithInfoFn;
        private final Map<String, Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>> dfCache = new HashMap<>();

        private SomethingDefer(
                MergedSelectionSet mergedSelectionSet,
                ExecutionStrategyParameters parameters,
                ExecutionContext executionContext,
                BiFunction<ExecutionContext, ExecutionStrategyParameters, CompletableFuture<FieldValueInfo>> resolveFieldWithInfoFn
        ) {
            this.executionContext = executionContext;
            this.resolveFieldWithInfoFn = resolveFieldWithInfoFn;
            ImmutableListMultimap.Builder<DeferExecution, MergedField> deferExecutionToFieldsBuilder = ImmutableListMultimap.builder();
            ImmutableSet.Builder<MergedField> deferredFieldsBuilder = ImmutableSet.builder();

            mergedSelectionSet.getSubFields().values().forEach(mergedField -> {
                mergedField.getDeferExecutions().forEach(de -> {
                    deferExecutionToFieldsBuilder.put(de, mergedField);
                    deferredFieldsBuilder.add(mergedField);
                });
            });

            this.deferExecutionToFields = deferExecutionToFieldsBuilder.build();
            this.deferredFields = deferredFieldsBuilder.build();
            this.parameters = parameters;
        }

        private boolean isDeferredField(MergedField mergedField) {
            return deferredFields.contains(mergedField);
        }

        private Set<DeferredCall> createCalls() {
            return deferExecutionToFields.keySet().stream()
                    .map(deferExecution -> {
                        DeferredErrorSupport errorSupport = new DeferredErrorSupport();

                        List<MergedField> mergedFields = deferExecutionToFields.get(deferExecution);

                        List<Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>> collect = mergedFields.stream()
                                .map(currentField -> {
                                    Map<String, MergedField> fields = new LinkedHashMap<>();
                                    fields.put(currentField.getName(), currentField);

                                    ExecutionStrategyParameters callParameters = parameters.transform(builder ->
                                            {
                                                MergedSelectionSet mergedSelectionSet = newMergedSelectionSet().subFields(fields).build();
                                                builder.deferredErrorSupport(errorSupport)
                                                        .field(currentField)
                                                        .fields(mergedSelectionSet)
                                                        .path(builder.path.segment(currentField.getName()))
                                                        .parent(null); // this is a break in the parent -> child chain - it's a new start effectively
                                            }
                                    );

                                    return dfCache.computeIfAbsent(
                                            currentField.getName(),
                                            // The same field can be associated with multiple defer executions, so
                                            // we memoize the field resolution to avoid multiple calls to the same data fetcher
                                            key -> FpKit.interThreadMemoize(() -> resolveFieldWithInfoFn
                                                    .apply(executionContext, callParameters)
                                                    .thenCompose(FieldValueInfo::getFieldValue)
                                                    .thenApply(executionResult -> new DeferredCall.FieldWithExecutionResult(currentField.getName(), executionResult)))
                                    );


                                })
                                .collect(Collectors.toList());

                        // with a deferred field we are really resetting where we execute from, that is from this current field onwards
                        return new DeferredCall(
                                deferExecution.getLabel(),
                                this.parameters.getPath(),
                                collect,
                                errorSupport
                        );
                    })
                    .collect(Collectors.toSet());
        }

    }
}
