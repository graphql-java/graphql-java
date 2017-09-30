package graphql.execution;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.language.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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

        InstrumentationContext<CompletableFuture<ExecutionResult>> executionStrategyCtx = executionContext.getInstrumentation().beginExecutionStrategy(new InstrumentationExecutionStrategyParameters(executionContext));
        Map<String, List<Field>> fields = parameters.fields();
        List<String> fieldNames = new ArrayList<>(fields.keySet());

        CompletableFuture<List<ExecutionResult>> resultsFuture;
        resultsFuture = Async.eachSequentially(fieldNames, (fieldName, index, prevResults) -> {
            try {
                List<Field> currentField = fields.get(fieldName);
                ExecutionPath fieldPath = parameters.path().segment(fieldName);
                ExecutionStrategyParameters newParameters = parameters
                        .transform(builder -> builder.field(currentField).path(fieldPath));
                return resolveField(executionContext, newParameters);
            } catch (AbortExecutionException abortException) {
                List<CompletableFuture<ExecutionResult>> futures = prevResults.stream()
                        .map(CompletableFuture::completedFuture).collect(Collectors.toList());
                CompletableFuture<ExecutionResult> partialResult = partialResult(executionContext, abortException, fieldNames, futures);
                throw new PartialResultException(partialResult);
            }
        });

        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        BiConsumer<List<ExecutionResult>, Throwable> listThrowableBiConsumer = handleResults(executionContext, fieldNames, overallResult);
        resultsFuture.whenComplete(listThrowableBiConsumer);

        executionStrategyCtx.onEnd(overallResult, null);
        return overallResult;
    }

}
