package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.Instrumentation;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationDataFetchParameters {
    private final ExecutionContext executionContext;

    public InstrumentationDataFetchParameters(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

}
