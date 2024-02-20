package graphql.execution.instrumentation.adapters;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.Internal;
import graphql.execution.FieldValueInfo;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.ExecuteObjectInstrumentationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A class to help adapt old {@link ExecutionResult} based ExecutionStrategyInstrumentationContext
 * from the newer {@link Map} based ones.
 */
@Internal
public class ExecuteObjectInstrumentationContextAdapter implements ExecuteObjectInstrumentationContext {

    private final ExecutionStrategyInstrumentationContext delegate;

    public ExecuteObjectInstrumentationContextAdapter(ExecutionStrategyInstrumentationContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onDispatched(CompletableFuture<Map<String, Object>> result) {
        CompletableFuture<ExecutionResult> future = result.thenApply(r -> ExecutionResultImpl.newExecutionResult().data(r).build());
        delegate.onDispatched(future);
        //
        // when the mapped future is completed, then call onCompleted on the delegate
        future.whenComplete(delegate::onCompleted);
    }

    @Override
    public void onCompleted(Map<String, Object> result, Throwable t) {
    }

    @Override
    public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
        delegate.onFieldValuesInfo(fieldValueInfoList);
    }

    @Override
    public void onFieldValuesException() {
        delegate.onFieldValuesException();
    }
}
