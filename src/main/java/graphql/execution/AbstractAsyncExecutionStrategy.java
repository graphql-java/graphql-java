package graphql.execution;

import com.google.common.collect.Maps;
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
        return executionContext.engineRun(results -> {
                    Map<String, Object> resolvedValuesByField = Maps.newLinkedHashMapWithExpectedSize(fieldNames.size());
                    int ix = 0;
                    for (Object result : results) {
                        String fieldName = fieldNames.get(ix++);
                        resolvedValuesByField.put(fieldName, result);
                    }
                    overallResult.complete(new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors()));
                },
                exception -> {
                    handleNonNullException(executionContext, overallResult, exception);
                });
    }
}
