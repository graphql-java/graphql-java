package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicSpi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static graphql.Assert.assertNotNull;


@NullMarked
@PublicSpi
public abstract class AbstractAsyncExecutionStrategy extends ExecutionStrategy {

    public AbstractAsyncExecutionStrategy() {
    }

    public AbstractAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    protected BiConsumer<@Nullable List<@Nullable Object>, @Nullable Throwable> handleResults(ExecutionContext executionContext, List<String> fieldNames, CompletableFuture<ExecutionResult> overallResult) {
        return (@Nullable List<@Nullable Object> results, @Nullable Throwable exception) -> {
            exception = executionContext.possibleCancellation(exception);

            if (exception != null) {
                // A cancellation that fired after some fields already completed arrives here as a
                // synthesised AbortExecutionException with a non-null results list (a real field
                // failure always has null results). When partial capture is enabled we keep those
                // results and attach the cancellation error; otherwise we report the error as usual.
                if (results != null && capturePartialResults(executionContext)) {
                    executionContext.addError((AbortExecutionException) exception);
                    completeResultFuture(overallResult, executionContext, fieldNames, results);
                    return;
                }
                handleNonNullException(executionContext, overallResult, exception);
                return;
            }

            completeResultFuture(overallResult, executionContext, fieldNames, assertNotNull(results, "Successful field completion must produce results"));
        };
    }

    protected void completeResultFuture(CompletableFuture<ExecutionResult> overallResult, ExecutionContext executionContext, List<String> fieldNames, List<@Nullable Object> results) {
        Map<String, @Nullable Object> resolvedValuesByField = executionContext.getResponseMapFactory().createInsertionOrdered(fieldNames, results);
        overallResult.complete(new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors()));
    }
}
