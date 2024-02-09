package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.nonNullCtx;

/**
 * Async non-blocking execution, but serial: only one field at the time will be resolved.
 * See {@link AsyncExecutionStrategy} for a non-serial (parallel) execution of every field.
 */
@PublicApi
public class AsyncSerialExecutionStrategy extends AbstractAsyncExecutionStrategy {

    public AsyncSerialExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    public AsyncSerialExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    @SuppressWarnings({"TypeParameterUnusedInFormals", "FutureReturnValueIgnored"})
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);
        InstrumentationContext<ExecutionResult> executionStrategyCtx = nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters,
                executionContext.getInstrumentationState())
        );
        MergedSelectionSet fields = parameters.getFields();
        ImmutableList<String> fieldNames = ImmutableList.copyOf(fields.keySet());

        CompletableFuture<List<Object>> resultsFuture = Async.eachSequentially(fieldNames, (fieldName, prevResults) -> {
            MergedField currentField = fields.getSubField(fieldName);
            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));
            return resolveField(executionContext, newParameters);
        });

        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        resultsFuture.whenComplete(handleResults(executionContext, fieldNames, overallResult));
        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

}
