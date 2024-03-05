package graphql.execution.instrumentation.adapters;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.Internal;
import graphql.execution.instrumentation.InstrumentationContext;

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
    public void onDispatched() {
        delegate.onDispatched();
    }

    @Override
    public void onCompleted(Object result, Throwable t) {
        if (t != null) {
            delegate.onCompleted(null, t);
        } else {
            delegate.onCompleted(ExecutionResultImpl.newExecutionResult().data(result).build(), null);
        }
    }
}
