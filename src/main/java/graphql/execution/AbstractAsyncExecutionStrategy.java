package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;


public abstract class AbstractAsyncExecutionStrategy extends ExecutionStrategy {

    public AbstractAsyncExecutionStrategy() {
    }

    public AbstractAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    protected void handleException(ExecutionContext executionContext, CompletableFuture<ExecutionResult> result, Throwable e) {
        if (e instanceof CompletionException && e.getCause() instanceof NonNullableFieldWasNullException) {
            assertNonNullFieldPrecondition((NonNullableFieldWasNullException) e.getCause(), result);
            if (!result.isDone()) {
                result.complete(new ExecutionResultImpl(null, executionContext.getErrors()));
            }
        } else {
            result.completeExceptionally(e);
        }
    }

    protected void completeCompletableFuture(ExecutionContext executionContext, List<String> fieldNames, List<CompletableFuture<ExecutionResult>> futures, CompletableFuture<ExecutionResult> result) {
        Map<String, Object> resolvedValuesByField = new LinkedHashMap<>();
        int ix = 0;
        for (CompletableFuture<ExecutionResult> future : futures) {

            if (future.isCompletedExceptionally()) {
                future.whenComplete((Null, e) -> handleException(executionContext, result, e));
                return;
            }
            String fieldName = fieldNames.get(ix++);
            ExecutionResult resolvedResult = future.join();
            resolvedValuesByField.put(fieldName, resolvedResult.getData());
        }
        result.complete(new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors()));
    }
}
