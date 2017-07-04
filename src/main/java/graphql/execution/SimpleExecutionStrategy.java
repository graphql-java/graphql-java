package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The standard graphql execution strategy that runs fields in serial order
 */
public class SimpleExecutionStrategy extends ExecutionStrategy {

    /**
     * The standard graphql execution strategy that runs fields in serial order
     */
    public SimpleExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates a simple execution handler that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use
     */
    public SimpleExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    public CompletionStage<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        Map<String, List<Field>> fields = parameters.fields();
        final Map<String, CompletableFuture<ExecutionResult>> results = new LinkedHashMap<>();
        List<CompletableFuture<ExecutionResult>> futures = new ArrayList<>();
        for (String fieldName : fields.keySet()) {
            List<Field> fieldList = fields.get(fieldName);

            ExecutionPath fieldPath = parameters.path().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters.transform(builder -> builder.path(fieldPath));

            try {

                CompletableFuture<ExecutionResult> future = resolveField(executionContext, newParameters, fieldList).toCompletableFuture();
                futures.add(future);
                results.put(fieldName, future);
            } catch (NonNullableFieldWasNullException e) {
                //TODO: return exception via CompletableFuture and not as a real exception
                assertNonNullFieldPrecondition(e);
                return CompletableFuture.completedFuture(null);
            }
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).thenApply((ignored) -> {
            Map<String, ExecutionResult> realResults = new LinkedHashMap<>();
            for (String field : results.keySet()) {
                realResults.put(field, results.get(field).join());

            }
            return new ExecutionResultImpl(realResults, executionContext.getErrors());
        });
    }
}
