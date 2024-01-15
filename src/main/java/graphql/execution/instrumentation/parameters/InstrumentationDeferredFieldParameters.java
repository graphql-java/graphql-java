package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.instrumentation.InstrumentationState;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
public class InstrumentationDeferredFieldParameters extends InstrumentationFieldParameters {

    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationDeferredFieldParameters(ExecutionContext executionContext, Supplier<ExecutionStepInfo> executionStepInfo, ExecutionStrategyParameters executionStrategyParameters) {
        this(executionContext, executionStepInfo, executionContext.getInstrumentationState(), executionStrategyParameters);
    }

    InstrumentationDeferredFieldParameters(ExecutionContext executionContext, Supplier<ExecutionStepInfo> executionStepInfo, InstrumentationState instrumentationState, ExecutionStrategyParameters executionStrategyParameters) {
        super(executionContext, executionStepInfo, instrumentationState);
        this.executionStrategyParameters = executionStrategyParameters;
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }
}
