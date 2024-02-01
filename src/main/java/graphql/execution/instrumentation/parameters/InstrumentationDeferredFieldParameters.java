package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.InstrumentationState;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
public class InstrumentationDeferredFieldParameters {
    private final ExecutionContext executionContext;
    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationDeferredFieldParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters) {
        this.executionContext = executionContext;
        this.executionStrategyParameters = executionStrategyParameters;
    }


    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }
}
