package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.execution.support.CallableWrapper;
import graphql.language.Field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * <p>ExecutorServiceExecutionStrategy uses an {@link ExecutorService} to parallelize the resolve.</p>
 *
 * Due to the nature of {@link #execute(ExecutionContext, ExecutionStrategyParameters)}  implementation, {@link ExecutorService}
 * MUST have the following 2 characteristics:
 * <ul>
 * <li>1. The underlying {@link java.util.concurrent.ThreadPoolExecutor} MUST have a reasonable {@code maximumPoolSize}
 * <li>2. The underlying {@link java.util.concurrent.ThreadPoolExecutor} SHALL NOT use its task queue.
 * </ul>
 *
 * <p>Failure to follow 1. and 2. can result in a very large number of threads created or hanging. (deadlock)</p>
 *
 * See {@code graphql.execution.ExecutorServiceExecutionStrategyTest} for example usage.
 */
@PublicApi
public class ExecutorServiceExecutionStrategy extends ExecutionStrategy {

    private final ExecutorService executorService;
    private final CallableWrapper callableWrapper;

    /**
     * Creates an {@link ExecutorServiceExecutionStrategy} with a {@link SimpleDataFetcherExceptionHandler} and
     * a no-op {@link CallableWrapper}
     */
    public ExecutorServiceExecutionStrategy(ExecutorService executorService) {
        this(executorService, new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates an {@link ExecutorServiceExecutionStrategy} with a no-op {@link CallableWrapper}
     */
    public ExecutorServiceExecutionStrategy(ExecutorService executorService, DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        this(executorService, dataFetcherExceptionHandler, new NoOpCallableWrapper());
    }

    public ExecutorServiceExecutionStrategy(ExecutorService executorService, DataFetcherExceptionHandler dataFetcherExceptionHandler, CallableWrapper callableWrapper) {
        super(dataFetcherExceptionHandler);
        this.executorService = executorService;
        this.callableWrapper = Objects.requireNonNull(callableWrapper);
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(final ExecutionContext executionContext, final ExecutionStrategyParameters parameters) {
        if (executorService == null)
            return new SimpleExecutionStrategy().execute(executionContext, parameters);

        Map<String, List<Field>> fields = parameters.fields();
        Map<String, Future<ExecutionResult>> futures = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            final List<Field> currentField = fields.get(fieldName);

            ExecutionPath fieldPath = parameters.path().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));

            Callable<ExecutionResult> fieldResolver = () -> resolveField(executionContext, newParameters).join();
            Callable<ExecutionResult> wrappedFieldResolver = callableWrapper.wrapCallable(fieldResolver);
            futures.put(fieldName, executorService.submit(wrappedFieldResolver));
        }
        try {
            Map<String, Object> results = new LinkedHashMap<>();
            for (String fieldName : futures.keySet()) {
                ExecutionResult executionResult = futures.get(fieldName).get();

                results.put(fieldName, executionResult != null ? executionResult.getData() : null);
            }
            return CompletableFuture.completedFuture(new ExecutionResultImpl(results, executionContext.getErrors()));
        } catch (InterruptedException | ExecutionException e) {
            throw new GraphQLException(e);
        }
    }

    private static class NoOpCallableWrapper implements CallableWrapper {
        @Override
        public <T> Callable<T> wrapCallable(Callable<T> callable) {
            return callable;
        }
    }
}
