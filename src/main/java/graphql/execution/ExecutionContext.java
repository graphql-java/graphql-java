package graphql.execution;


import graphql.GraphQLError;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionContext {

    private final GraphQLSchema graphQLSchema;
    private final ExecutionId executionId;
    private final InstrumentationState instrumentationState;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final OperationDefinition operationDefinition;
    private final Map<String, Object> variables;
    private final Object root;
    private final Object context;
    private final Map<String, GraphQLError> errors = new ConcurrentHashMap<>();
    private final Instrumentation instrumentation;

    public ExecutionContext(Instrumentation instrumentation, ExecutionId executionId, GraphQLSchema graphQLSchema, InstrumentationState instrumentationState, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, Map<String, FragmentDefinition> fragmentsByName, OperationDefinition operationDefinition, Map<String, Object> variables, Object context, Object root) {
        this.graphQLSchema = graphQLSchema;
        this.executionId = executionId;
        this.instrumentationState = instrumentationState;
        this.queryStrategy = queryStrategy;
        this.mutationStrategy = mutationStrategy;
        this.subscriptionStrategy = subscriptionStrategy;
        this.fragmentsByName = fragmentsByName;
        this.operationDefinition = operationDefinition;
        this.variables = variables;
        this.context = context;
        this.root = root;
        this.instrumentation = instrumentation;
    }


    public ExecutionId getExecutionId() {
        return executionId;
    }

    public InstrumentationState getInstrumentationState() {
        return instrumentationState;
    }

    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    public OperationDefinition getOperationDefinition() {
        return operationDefinition;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public Object getContext() {
        return context;
    }

    public <T> T getRoot() {
        //noinspection unchecked
        return (T) root;
    }

    public FragmentDefinition getFragment(String name) {
        return fragmentsByName.get(name);
    }

    public void addError(GraphQLError error, ExecutionPath path) {
        // see http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability about how per
        // field errors should be handled - ie only once per field
        String key = path.toString();
        if (!errors.containsKey(key)) {
            this.errors.put(key, error);
        }
    }

    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors.values());
    }

    public ExecutionStrategy getQueryStrategy() {
        return queryStrategy;
    }

    public ExecutionStrategy getMutationStrategy() {
        return mutationStrategy;
    }

    public ExecutionStrategy getSubscriptionStrategy() {
        return subscriptionStrategy;
    }
}
