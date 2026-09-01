package graphql.execution;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.incremental.DeferredExecutionSupport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.introspection.Introspection;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.Async.CombinedBuilder.OnCancel.DROP_PENDING;
import static graphql.execution.Async.CombinedBuilder.OnCancel.WAIT_FOR_PENDING;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
@PublicApi
@NullMarked
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
        DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = executionContext.getDataLoaderDispatcherStrategy();
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = ExecutionStrategyInstrumentationContext.nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters, executionContext.getInstrumentationState()));

        MergedSelectionSet fields = parameters.getFields();
        List<String> fieldNames = fields.getKeys();

        Optional<ExecutionResult> isNotSensible = Introspection.isIntrospectionSensible(fields, executionContext);
        if (isNotSensible.isPresent()) {
            return CompletableFuture.completedFuture(isNotSensible.get());
        }

        DeferredExecutionSupport deferredExecutionSupport = createDeferredExecutionSupport(executionContext, parameters);

        dataLoaderDispatcherStrategy.executionStrategy(executionContext, parameters, deferredExecutionSupport.getNonDeferredFieldNames(fieldNames).size());
        Async.CombinedBuilder<FieldValueInfo> futures = getAsyncFieldValueInfo(executionContext, parameters, deferredExecutionSupport);
        dataLoaderDispatcherStrategy.finishedFetching(executionContext, parameters);


        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched();

        CompletableFuture<Void> cancelCF = getCancellationFuture(executionContext);
        futures.await(cancelCF, DROP_PENDING).whenComplete((completeValueInfos, throwable) -> {
            List<String> fieldsExecutedOnInitialResult = deferredExecutionSupport.getNonDeferredFieldNames(fieldNames);

            throwable = executionContext.possibleCancellation(throwable);

            if (throwable != null && !(completeValueInfos != null && capturePartialResults(executionContext))) {
                // a genuine field failure, or a cancellation we cannot surface partial results for:
                // there is nothing usable to return, so just report the error
                handleResults(executionContext, fieldsExecutedOnInitialResult, overallResult).accept(null, throwable);
                return;
            }

            // normal completion, or capturePartialResults(..) on cancel: completeValueInfos holds the
            // FieldValueInfos that completed (with null entries for any cancelled before completing)
            completeFieldValues(executionContext, parameters, executionStrategyCtx, dataLoaderDispatcherStrategy,
                    completeValueInfos, fieldsExecutedOnInitialResult, cancelCF, overallResult);
        }).exceptionally((ex) -> {
            // if there are any issues with combining/handling the field results,
            // complete the future at all costs and bubble up any thrown exception so
            // the execution does not hang.
            dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesException(ex, parameters);
            executionStrategyCtx.onFieldValuesException();
            overallResult.completeExceptionally(ex);
            return null;
        });

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

    /**
     * Turns the completed {@link FieldValueInfo}s into field values and completes the {@code overallResult}.
     * <p>
     * When {@link #capturePartialResults(ExecutionContext)} is enabled {@code completeValueInfos} may
     * contain {@code null} entries for fields that were cancelled before they completed; those become
     * {@code null} field values and are excluded from the instrumentation callbacks.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private void completeFieldValues(ExecutionContext executionContext,
                                     ExecutionStrategyParameters parameters,
                                     ExecutionStrategyInstrumentationContext executionStrategyCtx,
                                     DataLoaderDispatchStrategy dataLoaderDispatcherStrategy,
                                     List<FieldValueInfo> completeValueInfos,
                                     List<String> fieldNames,
                                     @Nullable CompletableFuture<Void> cancelCF,
                                     CompletableFuture<ExecutionResult> overallResult) {
        Async.CombinedBuilder<Object> fieldValuesFutures = fieldValuesCombinedBuilder(completeValueInfos);
        // null entries only occur when capturePartialResults(..) is enabled; the instrumentation callbacks
        // should not see them, so filter them out (nonNullFieldValueInfos returns the list as-is if none)
        List<FieldValueInfo> valueInfosForInstrumentation = nonNullFieldValueInfos(completeValueInfos);
        dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesInfo(valueInfosForInstrumentation, parameters);
        executionStrategyCtx.onFieldValuesInfo(valueInfosForInstrumentation);
        // value-phase futures are cancellation-aware (nested object/list results self-complete with
        // their own partial data on cancel), so wait for them to settle rather than harvesting nulls
        fieldValuesFutures.await(cancelCF, WAIT_FOR_PENDING)
                .whenComplete(handleResults(executionContext, fieldNames, overallResult));
    }

}
