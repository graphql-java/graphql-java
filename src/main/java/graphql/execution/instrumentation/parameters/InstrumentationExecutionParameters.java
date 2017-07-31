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

    public InstrumentationExecutionParameters(ExecutionInput executionInput, InstrumentationState instrumentationState) {
        this(
                executionInput.getQuery(),
                executionInput.getOperationName(),
                executionInput.getContext(),
                executionInput.getVariables() != null ? executionInput.getVariables() : Collections.emptyMap(),
                instrumentationState);
    }

    public InstrumentationExecutionParameters(String query, String operation, Object context, Map<String, Object> variables, InstrumentationState instrumentationState) {
        this.query = query;
        this.operation = operation;
        this.context = context;
        this.variables = variables;
        this.instrumentationState = instrumentationState;
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
