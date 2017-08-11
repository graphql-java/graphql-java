package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.language.Field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class SerialExecutionStrategy extends ExecutionStrategy {

    public SerialExecutionStrategy() {
        super();
    }

    public SerialExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        InstrumentationContext<CompletableFuture<ExecutionResult>> executionStrategyCtx = executionContext.getInstrumentation().beginExecutionStrategy(new InstrumentationExecutionStrategyParameters(executionContext));

        Map<String, List<Field>> fields = parameters.fields();
        Map<String, Object> results = new LinkedHashMap<>();
        for (String fieldName : fields.keySet()) {
            List<Field> currentField = fields.get(fieldName);

            ExecutionPath fieldPath = parameters.path().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));

            try {
                ExecutionResult resolvedResult = resolveField(executionContext, newParameters).join();
                results.put(fieldName, resolvedResult.getData());
            } catch (CompletionException e) {
                if (e.getCause() instanceof NonNullableFieldWasNullException) {
                    assertNonNullFieldPrecondition((NonNullableFieldWasNullException) e.getCause());
                    results = null;
                    break;
                } else {
                    throw e;
                }

            }
        }
        CompletableFuture<ExecutionResult> result = completedFuture(new ExecutionResultImpl(results, executionContext.getErrors()));

        executionStrategyCtx.onEnd(result, null);
        return result;
    }
}
