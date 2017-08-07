package graphql.execution;

import graphql.ExecutionResult;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async non-blocking execution, but serial: only one field at the the time will be resolved.
 * See {@link AsyncExecutionStrategy} for a non serial (parallel) execution of every field.
 */
public class AsyncSerialExecutionStrategy extends AbstractAsyncExecutionStrategy {

    public AsyncSerialExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    public AsyncSerialExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        CompletableFuture<ExecutionResult> result = new CompletableFuture<>();
        resolveNthField(executionContext, parameters, 0, new ArrayList<>(), result);

        return result;
    }

    private void resolveNthField(ExecutionContext executionContext,
                                 ExecutionStrategyParameters parameters,
                                 int index,
                                 List<CompletableFuture<ExecutionResult>> allFutures,
                                 CompletableFuture<ExecutionResult> overallResult) {
        Map<String, List<Field>> fields = parameters.fields();
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        String fieldName = fieldNames.get(index);

        List<Field> currentField = fields.get(fieldName);
        ExecutionPath fieldPath = parameters.path().segment(fieldName);
        ExecutionStrategyParameters newParameters = parameters
                .transform(builder -> builder.field(currentField).path(fieldPath));
        CompletableFuture<ExecutionResult> future = resolveField(executionContext, newParameters);
        future.whenComplete((notUsed1, notUsed2) -> {
            allFutures.add(future);
            if (index + 1 == fields.size()) {
                completeCompletableFuture(executionContext, fieldNames, allFutures, overallResult);
            } else {
                resolveNthField(executionContext, parameters, index + 1, allFutures, overallResult);
            }
        });
    }


}
