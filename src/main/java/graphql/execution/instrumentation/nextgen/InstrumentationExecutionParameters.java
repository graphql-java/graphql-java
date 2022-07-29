package graphql.execution.instrumentation.nextgen;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.schema.GraphQLSchema;

import java.util.Map;

/**
 * Parameters sent to {@link graphql.execution.instrumentation.nextgen.Instrumentation} methods
 */
@Internal
public class InstrumentationExecutionParameters {
    private final ExecutionInput executionInput;
    private final String query;
    private final String operation;
    private final Object context;
    private final GraphQLContext graphQLContext;
    private final Map<String, Object> variables;
    private final InstrumentationState instrumentationState;
    private final GraphQLSchema schema;

    public InstrumentationExecutionParameters(ExecutionInput executionInput, GraphQLSchema schema, InstrumentationState instrumentationState) {
        this.executionInput = executionInput;
        this.query = executionInput.getQuery();
        this.operation = executionInput.getOperationName();
        this.context = executionInput.getContext();
        this.graphQLContext = executionInput.getGraphQLContext();
        this.variables = executionInput.getVariables() != null ? executionInput.getVariables() : ImmutableKit.emptyMap();
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
    public InstrumentationExecutionParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationExecutionParameters(this.getExecutionInput(), this.schema, instrumentationState);
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
    @Deprecated
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

    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }

    public GraphQLSchema getSchema() {
        return this.schema;
    }
}
