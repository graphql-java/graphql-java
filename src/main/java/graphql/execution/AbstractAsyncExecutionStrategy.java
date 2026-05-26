package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicSpi;

import java.util.concurrent.CompletionException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;


@PublicSpi
public abstract class AbstractAsyncExecutionStrategy extends ExecutionStrategy {

    public AbstractAsyncExecutionStrategy() {
    }

    public AbstractAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    protected BiConsumer<List<Object>, Throwable> handleResults(ExecutionContext executionContext, List<String> fieldNames, CompletableFuture<ExecutionResult> overallResult) {
        return (List<Object> results, Throwable exception) -> {
            exception = executionContext.possibleCancellation(exception);

            if (exception != null) {
                handleNonNullException(executionContext, overallResult, exception);
                return;
            }

            completeResultFuture(overallResult, executionContext, fieldNames, results);
        };
    }

    protected BiConsumer<List<Object>, Throwable> handleResultsWithPartialData(ExecutionContext executionContext, List<String> fieldNames, CompletableFuture<ExecutionResult> overallResult) {
        return (List<Object> results, Throwable exception) -> {
            // when partial results on cancel is enabled the results list will already have partial data
            // (already-completed fields) so we can build a partial response even if exception is set
            if (exception != null) {
                Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
                if (cause instanceof AbortExecutionException && results != null
                        && capturePartialResults(executionContext)) {
                    executionContext.addError((AbortExecutionException) cause);
                    completeResultFuture(overallResult, executionContext, fieldNames, results);
                    return;
                }
                handleNonNullException(executionContext, overallResult, exception);
                return;
            }

            // check if cancel fired while results were being gathered (no exception, but cancelled)
            Throwable cancelException = executionContext.possibleCancellation(null);
            if (cancelException != null) {
                Throwable cancelCause = cancelException instanceof CompletionException ? cancelException.getCause() : cancelException;
                if (cancelCause instanceof AbortExecutionException && results != null
                        && capturePartialResults(executionContext)) {
                    // we have partial data from already-completed CFs — use it
                    executionContext.addError((AbortExecutionException) cancelCause);
                    completeResultFuture(overallResult, executionContext, fieldNames, results);
                } else {
                    handleNonNullException(executionContext, overallResult, cancelException);
                }
                return;
            }

            completeResultFuture(overallResult, executionContext, fieldNames, results);
        };
    }

    protected void completeResultFuture(CompletableFuture<ExecutionResult> overallResult, ExecutionContext executionContext, List<String> fieldNames, List<Object> results) {
        Map<String, Object> resolvedValuesByField = executionContext.getResponseMapFactory().createInsertionOrdered(fieldNames, results);
        overallResult.complete(new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors()));
    }
}
