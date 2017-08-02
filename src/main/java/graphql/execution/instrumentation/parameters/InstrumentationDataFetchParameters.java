package graphql.execution.instrumentation.parameters;

import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationDataFetchParameters {
    private final ExecutionContext executionContext;
    private final InstrumentationState instrumentationState;

    public InstrumentationDataFetchParameters(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.instrumentationState = executionContext.getInstrumentationState();
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }
}
