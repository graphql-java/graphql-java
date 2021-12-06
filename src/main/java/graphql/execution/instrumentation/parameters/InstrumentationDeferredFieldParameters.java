package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.InstrumentationState;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
public class InstrumentationDeferredFieldParameters extends InstrumentationFieldParameters {

    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationDeferredFieldParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, ExecutionStepInfo executionStepInfo) {
        this(executionContext, executionStrategyParameters, executionStepInfo, executionContext.getInstrumentationState());
    }

    InstrumentationDeferredFieldParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, ExecutionStepInfo executionStepInfo, InstrumentationState instrumentationState) {
        super(executionContext, () -> executionStepInfo, instrumentationState);
        this.executionStrategyParameters = executionStrategyParameters;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    @Override
    public InstrumentationDeferredFieldParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationDeferredFieldParameters(
                this.getExecutionContext(), this.executionStrategyParameters, this.getExecutionStepInfo(), instrumentationState);
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }
}
