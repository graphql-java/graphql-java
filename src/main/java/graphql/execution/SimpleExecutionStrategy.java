package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.language.Field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

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
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        Map<String, List<Field>> fields = parameters.fields();
        Map<String, Object> results = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            List<Field> currentField = fields.get(fieldName);

            ExecutionPath fieldPath = parameters.path().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));

            try {
                ExecutionResult resolvedResult = resolveField(executionContext, newParameters).join();

                results.put(fieldName, resolvedResult != null ? resolvedResult.getData() : null);
            } catch (NonNullableFieldWasNullException e) {
                assertNonNullFieldPrecondition(e);
                results = null;
                break;
            }
        }
        return completedFuture(new ExecutionResultImpl(results, executionContext.getErrors()));
    }
}
