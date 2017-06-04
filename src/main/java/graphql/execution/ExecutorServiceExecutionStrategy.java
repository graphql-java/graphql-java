package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.Field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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

    ExecutorService executorService;

    public ExecutorServiceExecutionStrategy(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public ExecutionResult execute(final ExecutionContext executionContext, final ExecutionStrategyParameters parameters) {
        if (executorService == null)
            return new SimpleExecutionStrategy().execute(executionContext,parameters);

        Map<String, List<Field>> fields = parameters.fields();
        Map<String, Future<ExecutionResult>> futures = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            final List<Field> fieldList = fields.get(fieldName);
            Callable<ExecutionResult> resolveField = () -> resolveField(executionContext, parameters, fieldList);
            futures.put(fieldName, executorService.submit(resolveField));
        }
        try {
            Map<String, Object> results = new LinkedHashMap<>();
            for (String fieldName : futures.keySet()) {
                ExecutionResult executionResult = futures.get(fieldName).get();

                results.put(fieldName, executionResult != null ? executionResult.getData() : null);
            }
            return new ExecutionResultImpl(results, executionContext.getErrors());
        } catch (InterruptedException | ExecutionException e) {
            throw new GraphQLException(e);
        }
    }
}
