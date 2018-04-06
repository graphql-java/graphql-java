package graphql.execution;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.language.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.DeferredNodes.deferedNode;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
public class AsyncExecutionStrategy extends AbstractAsyncExecutionStrategy {

    private static final Logger log = LoggerFactory.getLogger(AsyncExecutionStrategy.class);

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
        return execute(executionContext, parameters, false);
    }

    CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters,
                                                      boolean skipDeferred) throws NonNullableFieldWasNullException {

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        InstrumentationContext<ExecutionResult> executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        Map<String, List<Field>> fields = parameters.fields();
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        List<CompletableFuture<ExecutionResult>> futures = new ArrayList<>();
        for (String fieldName : fieldNames) {
            if (skipDeferred) {
                boolean nonDefer = false;
                for (Field field : fields.get(fieldName)) {
                    if (!deferedNode(field.getDirectives())) {
                        nonDefer = true;
                    }
                }
                if (!nonDefer) {
                    parameters.fields().remove(fieldName);    // since skipping it, drop from list of fieldNames
                    log.debug("executeWithoutDeferred skipping field: {}", fieldName);
                    continue;
                }
            }

            List<Field> currentField = fields.get(fieldName);

            ExecutionPath fieldPath = parameters.path().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));

            CompletableFuture<ExecutionResult> future = resolveField(executionContext, newParameters, skipDeferred);
            if (future != null) futures.add(future);
        }

        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        Async.each(futures).whenComplete(handleResults(executionContext, fieldNames, overallResult));

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

}
