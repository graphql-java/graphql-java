package graphql.execution.instrumentation.parameters;

import graphql.PublicApi;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import org.jspecify.annotations.NullMarked;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@NullMarked
@PublicApi
public class InstrumentationExecutionStrategyParameters {

    private final ExecutionContext executionContext;
    private final ExecutionStrategyParameters executionStrategyParameters;

    public InstrumentationExecutionStrategyParameters(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters) {
        this.executionContext = executionContext;
        this.executionStrategyParameters = executionStrategyParameters;
    }


    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public ExecutionStrategyParameters getExecutionStrategyParameters() {
        return executionStrategyParameters;
    }

}
