package graphql.execution.instrumentation.adapters;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.Internal;
import graphql.execution.instrumentation.InstrumentationContext;

import java.util.concurrent.CompletableFuture;

/**
 * A class to help adapt old {@link ExecutionResult} based InstrumentationContext
 * from the newer {@link Object} based ones.
 */
@Internal
public class ExecutionResultInstrumentationContextAdapter implements InstrumentationContext<Object> {

    private final InstrumentationContext<ExecutionResult> delegate;

    public ExecutionResultInstrumentationContextAdapter(InstrumentationContext<ExecutionResult> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onDispatched(CompletableFuture<Object> result) {
        CompletableFuture<ExecutionResult> future = result.thenApply(obj -> ExecutionResultImpl.newExecutionResult().data(obj).build());
        delegate.onDispatched(future);
        //
        // when the mapped future is completed, then call onCompleted on the delegate
        future.whenComplete(delegate::onCompleted);
    }

    @Override
    public void onCompleted(Object result, Throwable t) {
    }
}
