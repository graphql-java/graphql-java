package graphql.execution;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
public class AsyncExecutionStrategy extends AbstractAsyncExecutionStrategy {

    /**
     * The standard graphql execution strategy that runs fields asynchronously
     */
    public AsyncExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates a execution strategy that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use
     */
    public AsyncExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext);

        InstrumentationContext<CompletableFuture<ExecutionResult>> executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        InstrumentationContext<Map<String, List<Field>>> beginFieldsCtx = instrumentation.beginFields(instrumentationParameters);

        Map<String, List<Field>> fields = parameters.fields();
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        List<CompletableFuture<ExecutionResult>> futures = new ArrayList<>();
        for (String fieldName : fieldNames) {
            List<Field> currentField = fields.get(fieldName);

            ExecutionPath fieldPath = parameters.path().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));

            CompletableFuture<ExecutionResult> future = resolveField(executionContext, newParameters);
            futures.add(future);
        }
        beginFieldsCtx.onEnd(fields, null);

        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        Async.each(futures).whenComplete(handleResults(executionContext, fieldNames, overallResult));

        executionStrategyCtx.onEnd(overallResult, null);
        return overallResult;
    }

}
