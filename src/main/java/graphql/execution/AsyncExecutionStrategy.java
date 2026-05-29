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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
        futures.await(cancelCF).whenComplete((completeValueInfos, throwable) -> {
            List<String> fieldsExecutedOnInitialResult = deferredExecutionSupport.getNonDeferredFieldNames(fieldNames);

            throwable = executionContext.possibleCancellation(throwable);

            if (throwable != null && !(completeValueInfos != null && capturePartialResults(executionContext))) {
                // a genuine field failure, or a cancellation we cannot surface partial results for:
                // there is nothing usable to return, so just report the error
                handleResults(executionContext, fieldsExecutedOnInitialResult, overallResult).accept(null, throwable);
                return;
            }

            // normal completion, or partial-results-on-cancel: completeValueInfos holds the
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
     * When partial-results-on-cancel is in play {@code completeValueInfos} may contain {@code null}
     * entries for fields that were cancelled before they completed; those become {@code null} field
     * values and are excluded from the instrumentation callbacks.
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
        Async.CombinedBuilder<Object> fieldValuesFutures = Async.ofExpectedSize(completeValueInfos.size());
        boolean hasNulls = false;
        for (FieldValueInfo completeValueInfo : completeValueInfos) {
            if (completeValueInfo != null) {
                fieldValuesFutures.addObject(completeValueInfo.getFieldValueObject());
            } else {
                hasNulls = true;
                fieldValuesFutures.addObject((Object) null);
            }
        }
        // null entries only occur for partial-results-on-cancel; the instrumentation callbacks should
        // not see them, so filter only when needed and otherwise pass the list straight through
        List<FieldValueInfo> valueInfosForInstrumentation = hasNulls
                ? completeValueInfos.stream().filter(Objects::nonNull).collect(Collectors.toList())
                : completeValueInfos;
        dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesInfo(valueInfosForInstrumentation, parameters);
        executionStrategyCtx.onFieldValuesInfo(valueInfosForInstrumentation);
        fieldValuesFutures.await(cancelCF).whenComplete(handleResults(executionContext, fieldNames, overallResult));
    }

}
