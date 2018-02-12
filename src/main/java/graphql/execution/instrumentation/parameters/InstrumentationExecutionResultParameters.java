package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.Map;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@PublicApi
public class InstrumentationExecutionResultParameters {
    private final ExecutionInput executionInput;
    private final String query;
    private final String operation;
    private final Object context;
    private final Map<String, Object> variables;
    private final InstrumentationState instrumentationState;
    private final GraphQLSchema schema;

    public InstrumentationExecutionResultParameters(ExecutionInput executionInput, GraphQLSchema schema, InstrumentationState instrumentationState) {
        this.executionInput = executionInput;
        this.query = executionInput.getQuery();
        this.operation = executionInput.getOperationName();
        this.context = executionInput.getContext();
        this.variables = executionInput.getVariables() != null ? executionInput.getVariables() : Collections.emptyMap();
        this.instrumentationState = instrumentationState;
        this.schema = schema;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    public InstrumentationExecutionResultParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationExecutionResultParameters(this.getExecutionInput(), this.schema, instrumentationState);
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

    public GraphQLSchema getSchema() {
        return this.schema;
    }


}
