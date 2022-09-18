package graphql.execution;

import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;


/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
@PublicApi
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
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Instrumentation instrumentation = executionContext.getInstrumentation();
        //
        // Standard allocation of instrumentation parameters.  Not overly heavy weight since it's a holder of objects - debatable tomove to
        // a supplier pattern since even a `() -> new InstrumentationExecutionStrategyParameters(...)` involves the allocation
        // of a lambda object with parameter capture.
        //
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        //
        // This is smart - if the instrumentation returns null - then a static no op is used - no allocation
        // This allows an Instrumentation to move to null contexts if they have none.
        // However, since `ChainedInstrumentation` is the most common form here, then there will always
        // be a graphql.execution.instrumentation.ChainedInstrumentation.ChainedInstrumentationContext allocated
        // back.
        //
        // We could be smarted here - if there is 0 or 1 instrumentation in the chain, and they return null, so could the chained instrumentation
        // code return a NOOP.
        //
        ExecutionStrategyInstrumentationContext executionStrategyCtx = ExecutionStrategyInstrumentationContext.nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters, executionContext.getInstrumentationState()));

        MergedSelectionSet fields = parameters.getFields();
        Set<String> fieldNames = fields.keySet();
        //
        // Async.CombinedBuilder is an array of CompletableFutures.  It sized by the number of fields
        // in the sub selection.  Since this method gets called back during object descending, we create onf of these for
        // every object that has a field selection.
        //
        // It's quite a nice alternative to the rubbish java.util.concurrent.CompletableFuture.allOf()
        //
        //
        Async.CombinedBuilder<FieldValueInfo> futures = Async.ofExpectedSize(fields.size());
        //
        // a list of the names of the fields - it's really a copy of Set<String> fieldNames above - candidate
        // to be removed honestly
        //
        List<String> resolvedFields = new ArrayList<>(fieldNames.size());
        for (String fieldName : fieldNames) {
            MergedField currentField = fields.getSubField(fieldName);

            //
            // Creation of a field path for that field
            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            //
            // This takes the current ES parameters tweaks them and allows them to point upwards to the
            // parent parameters.
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));

            resolvedFields.add(fieldName);
            CompletableFuture<FieldValueInfo> future = resolveFieldWithInfo(executionContext, newParameters);
            futures.add(future);
        }
        //
        // CF of the execution result.  When this is the actual overall result (first entry) - then this makes sense
        // as a returned value.  How-ever for inner calls to the ES (per object selection) we create a CF<ExecutionResult>
        // only to unwrap them and stick them into a map as values.
        //
        // The calling of the ES again (copied form graphql-js) is a candidate for enhancement.  Have a new "executeInner"
        // that returns a more specific and less heavy-weight value.
        //
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        futures.await().whenComplete((completeValueInfos, throwable) -> {
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, resolvedFields, overallResult);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }
            //
            // Allocation again of an array of CFs that needs to all complete to combine them into one value
            // This has a need trick where it optimised to returns zero / single or many version of itself
            // to reduce memory usage - don't use a list if there is 1 field
            //
            Async.CombinedBuilder<ExecutionResult> executionResultFutures = Async.ofExpectedSize(completeValueInfos.size());
            for (FieldValueInfo completeValueInfo : completeValueInfos) {
                executionResultFutures.add(completeValueInfo.getFieldValue());
            }
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
            executionResultFutures.await().whenComplete(handleResultsConsumer);
        }).exceptionally((ex) -> {
            // if there are any issues with combining/handling the field results,
            // complete the future at all costs and bubble up any thrown exception so
            // the execution does not hang.
            executionStrategyCtx.onFieldValuesException();
            overallResult.completeExceptionally(ex);
            return null;
        });

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }
}
