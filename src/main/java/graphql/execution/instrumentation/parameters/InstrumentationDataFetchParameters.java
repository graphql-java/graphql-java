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
        this(executionContext, executionContext.getInstrumentationState());
    }

    private InstrumentationDataFetchParameters(ExecutionContext executionContext, InstrumentationState instrumentationState) {
        this.executionContext = executionContext;
        this.instrumentationState = instrumentationState;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    public InstrumentationDataFetchParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationDataFetchParameters(executionContext, instrumentationState);
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }
}
