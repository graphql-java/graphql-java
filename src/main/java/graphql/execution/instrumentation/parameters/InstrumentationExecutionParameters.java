package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;

import java.util.Collections;
import java.util.Map;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@PublicApi
public class InstrumentationExecutionParameters {
    private final String query;
    private final String operation;
    private final Object context;
    private final Map<String, Object> variables;
    private final InstrumentationState instrumentationState;
    private ExecutionInput executionInput;

    public InstrumentationExecutionParameters(ExecutionInput executionInput, InstrumentationState instrumentationState) {
        this(
                executionInput.getQuery(),
                executionInput.getOperationName(),
                executionInput.getContext(),
                executionInput.getVariables() != null ? executionInput.getVariables() : Collections.emptyMap(),
                instrumentationState);
        this.executionInput = executionInput;
    }

    private InstrumentationExecutionParameters(String query, String operation, Object context, Map<String, Object> variables, InstrumentationState instrumentationState) {
        this.query = query;
        this.operation = operation;
        this.context = context;
        this.variables = variables;
        this.instrumentationState = instrumentationState;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    public InstrumentationExecutionParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationExecutionParameters(this.getExecutionInput(), instrumentationState);
    }

    public ExecutionInput getExecutionInput() {
        return executionInput;
    }

    public String getQuery() {
        return query;
    }

    public String getOperation() {
        return operation;
    }

    @SuppressWarnings("unchecked")
    public <T> T getContext() {
        return (T) context;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }


}
