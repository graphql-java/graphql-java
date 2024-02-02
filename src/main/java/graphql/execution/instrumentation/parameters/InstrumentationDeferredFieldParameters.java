package graphql.execution.instrumentation.parameters;

import graphql.ExperimentalApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@ExperimentalApi
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
