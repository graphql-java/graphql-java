package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.collect.ImmutableKit;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLSchema;

import java.util.Map;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@PublicApi
public class InstrumentationExecutionParameters {
    private final ExecutionInput executionInput;
    private final String query;
    private final String operation;
    private final Object context;
    private final GraphQLContext graphQLContext;
    private final Map<String, Object> variables;
    private final GraphQLSchema schema;

    public InstrumentationExecutionParameters(ExecutionInput executionInput, GraphQLSchema schema) {
        this.executionInput = executionInput;
        this.query = executionInput.getQuery();
        this.operation = executionInput.getOperationName();
        this.context = executionInput.getContext();
        this.graphQLContext = executionInput.getGraphQLContext();
        this.variables = executionInput.getVariables() != null ? executionInput.getVariables() : ImmutableKit.emptyMap();
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

    /**
     * @param <T> for two
     *
     * @return the legacy context
     *
     * @deprecated use {@link #getGraphQLContext()} instead
     */
    @Deprecated(since = "2021-07-05")
    @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
    public <T> T getContext() {
        return (T) context;
    }

    public GraphQLContext getGraphQLContext() {
        return graphQLContext;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }


    public GraphQLSchema getSchema() {
        return this.schema;
    }
}
