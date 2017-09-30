package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;


public abstract class AbstractAsyncExecutionStrategy extends ExecutionStrategy {

    public AbstractAsyncExecutionStrategy() {
    }

    public AbstractAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    protected BiConsumer<List<ExecutionResult>, Throwable> handleResults(ExecutionContext executionContext, List<String> fieldNames, CompletableFuture<ExecutionResult> overallResult) {
        return (List<ExecutionResult> results, Throwable exception) -> {
            if (exception != null) {
                if (exception instanceof CompletionException && exception.getCause() instanceof PartialResultException) {
                    PartialResultException partialResultException = (PartialResultException) exception.getCause();
                    overallResult.complete(partialResultException.getPartialResult());
                } else {
                    handleNonNullException(executionContext, overallResult, exception);
                }
                return;
            }
            Map<String, Object> resolvedValuesByField = new LinkedHashMap<>();
            int ix = 0;
            for (ExecutionResult executionResult : results) {

                String fieldName = fieldNames.get(ix++);
                resolvedValuesByField.put(fieldName, executionResult.getData());
            }
            overallResult.complete(new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors()));
        };
    }

    /**
     * This will return a partial result if some part of the code threw an AbortExecutionException to stop
     * the code processing
     *
     * @param executionContext the context in play
     * @param abortException   the exception that called for a halt to execution
     * @param fieldNames       the list of field names
     * @param futures          the list of futures dispatched so far
     *
     * @return a partial result
     */
    protected CompletableFuture<ExecutionResult> partialResult(ExecutionContext executionContext, AbortExecutionException abortException, List<String> fieldNames, List<CompletableFuture<ExecutionResult>> futures) {
        Map<String, Object> resolvedValuesByField = new LinkedHashMap<>();
        int ix = 0;
        for (CompletableFuture<ExecutionResult> future : futures) {
            String fieldName = fieldNames.get(ix++);
            if (future.isDone() && !future.isCompletedExceptionally()) {
                ExecutionResult executionResult = future.join();
                resolvedValuesByField.put(fieldName, executionResult.getData());
            }
        }
        List<GraphQLError> errors = new ArrayList<>();
        errors.addAll(executionContext.getErrors());
        if (!abortException.getUnderlyingErrors().isEmpty()) {
            errors.addAll(abortException.getUnderlyingErrors());
        } else {
            errors.add(abortException);
        }
        ExecutionResultImpl executionResult = new ExecutionResultImpl(resolvedValuesByField, errors);
        return CompletableFuture.completedFuture(executionResult);
    }

    protected class PartialResultException extends RuntimeException {
        private final CompletableFuture<ExecutionResult> partialResult;

        public PartialResultException(CompletableFuture<ExecutionResult> partialResult) {
            this.partialResult = partialResult;
        }

        public ExecutionResult getPartialResult() {
            return partialResult.join();
        }
    }


}
