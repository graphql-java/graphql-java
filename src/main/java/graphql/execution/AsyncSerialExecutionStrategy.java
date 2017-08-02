package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class AsyncSerialExecutionStrategy extends ExecutionStrategy {

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
                futuresCompleted(executionContext, fieldNames, allFutures, overallResult);
            } else {
                resolveNthField(executionContext, parameters, index + 1, allFutures, overallResult);
            }
        });
    }

    private void futuresCompleted(ExecutionContext executionContext,
                                  List<String> fieldNames,
                                  List<CompletableFuture<ExecutionResult>> futures,
                                  CompletableFuture<ExecutionResult> result) {
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

    private void handleException(ExecutionContext executionContext, CompletableFuture<ExecutionResult> result, Throwable e) {
        if (e instanceof CompletionException && e.getCause() instanceof NonNullableFieldWasNullException) {
            assertNonNullFieldPrecondition((NonNullableFieldWasNullException) e.getCause(), result);
            if (!result.isDone()) {
                result.complete(new ExecutionResultImpl(null, executionContext.getErrors()));
            }
        } else {
            result.completeExceptionally(e);
        }
    }

}
