package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicSpi;

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
            if (exception != null) {
                handleNonNullException(executionContext, overallResult, exception);
                return;
            }

            // No exception, but cancellation may have fired while the already-completed field values
            // were being gathered. When it has, the results list already holds the partial data from
            // the fields that completed before cancellation, so we can return it alongside the error.
            Throwable cancelException = executionContext.possibleCancellation(null);
            if (cancelException != null) {
                if (capturePartialResults(executionContext)) {
                    executionContext.addError((AbortExecutionException) cancelException);
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
