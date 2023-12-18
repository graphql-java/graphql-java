package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStrategyParameters;

import java.util.function.Supplier;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
public class InstrumentationDeferredFieldParameters extends InstrumentationFieldParameters {

    private final ExecutionStrategyParameters executionStrategyParameters;


    public InstrumentationDeferredFieldParameters(ExecutionContext executionContext, Supplier<ExecutionStepInfo> executionStepInfo, ExecutionStrategyParameters executionStrategyParameters) {
        super(executionContext, executionStepInfo);
        this.executionStrategyParameters = executionStrategyParameters;
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }
}
