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
     *
     * @deprecated state is now passed in direct to instrumentation methods
     */
    @Deprecated
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

    /**
     * Previously the instrumentation parameters had access to the state created via {@link Instrumentation#createState(InstrumentationCreateStateParameters)} but now
     * to save object allocations, the state is passed directly into instrumentation methods
     *
     * @param <T> for two
     *
     * @return the state created previously during a call to {@link Instrumentation#createState(InstrumentationCreateStateParameters)}
     *
     * @deprecated state is now passed in direct to instrumentation methods
     */
    @Deprecated
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <T extends InstrumentationState> T getInstrumentationState() {
        //noinspection unchecked
        return (T) instrumentationState;
    }

    public GraphQLSchema getSchema() {
        return this.schema;
    }
}
