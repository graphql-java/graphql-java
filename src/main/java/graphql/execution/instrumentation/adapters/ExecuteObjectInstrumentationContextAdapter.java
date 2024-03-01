package graphql.execution.instrumentation.adapters;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.Internal;
import graphql.execution.FieldValueInfo;
import graphql.execution.instrumentation.ExecuteObjectInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;

import java.util.List;
import java.util.Map;

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
    public void onDispatched() {
        delegate.onDispatched();
    }

    @Override
    public void onCompleted(Map<String, Object> result, Throwable t) {
        if (t != null) {
            delegate.onCompleted(null, t);
        } else {
            delegate.onCompleted(ExecutionResultImpl.newExecutionResult().data(result).build(), null);
        }
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
