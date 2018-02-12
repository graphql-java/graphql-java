package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.Map;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.Instrumentation} methods
 */
@PublicApi
public class InstrumentationCreatePreExecutionStateParameters {
    private final ExecutionInput executionInput;
    private final String query;
    private final String operation;
    private final Object context;
    private final Map<String, Object> variables;
    private final GraphQLSchema schema;

    public InstrumentationCreatePreExecutionStateParameters(ExecutionInput executionInput, GraphQLSchema schema) {
        this.executionInput = executionInput;
        this.query = executionInput.getQuery();
        this.operation = executionInput.getOperationName();
        this.context = executionInput.getContext();
        this.variables = executionInput.getVariables() != null ? executionInput.getVariables() : Collections.emptyMap();
        this.schema = schema;
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

    public GraphQLSchema getSchema() {
        return this.schema;
    }


}
