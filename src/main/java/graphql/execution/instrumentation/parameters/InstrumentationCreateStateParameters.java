package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.instrumentation.InstrumentationPreExecutionState;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.Map;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@PublicApi
public class InstrumentationCreateStateParameters {
    private final ExecutionInput executionInput;
    private final String query;
    private final String operation;
    private final Object context;
    private final Map<String, Object> variables;
    private final InstrumentationPreExecutionState preExecutionState;
    private final GraphQLSchema schema;

    public InstrumentationCreateStateParameters(ExecutionInput executionInput, GraphQLSchema schema, InstrumentationPreExecutionState preExecutionState) {
        this.executionInput = executionInput;
        this.query = executionInput.getQuery();
        this.operation = executionInput.getOperationName();
        this.context = executionInput.getContext();
        this.variables = executionInput.getVariables() != null ? executionInput.getVariables() : Collections.emptyMap();
        this.preExecutionState = preExecutionState;
        this.schema = schema;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param preExecutionState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    public InstrumentationCreateStateParameters withNewState(InstrumentationPreExecutionState preExecutionState) {
        return new InstrumentationCreateStateParameters(this.getExecutionInput(), this.schema, preExecutionState);
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

    public <T extends InstrumentationPreExecutionState> T getPreExecutionState() {
        //noinspection unchecked
        return (T) preExecutionState;
    }

    public GraphQLSchema getSchema() {
        return this.schema;
    }


}
