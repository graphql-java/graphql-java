package graphql.execution;


import graphql.GraphQLError;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ExecutionContext {

    public static ExecutionContextBuilder newContext() {
        return new ExecutionContextBuilder();
    }

    private final GraphQLSchema graphQLSchema;
    private final ExecutionId executionId;
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final OperationDefinition operationDefinition;
    private final Map<String, Object> variables;
    private final Object root;
    private final List<GraphQLError> errors = new CopyOnWriteArrayList<GraphQLError>();
    private final ExecutionConstraints executionConstraints;

    ExecutionContext(ExecutionId executionId, ExecutionConstraints executionConstraints, GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, Map<String, FragmentDefinition> fragmentsByName, OperationDefinition operationDefinition, Map<String, Object> variables, Object root) {
        this.executionId = executionId;
        this.executionConstraints = executionConstraints;
        this.graphQLSchema = graphQLSchema;
        this.queryStrategy = queryStrategy;
        this.mutationStrategy = mutationStrategy;
        this.fragmentsByName = fragmentsByName;
        this.operationDefinition = operationDefinition;
        this.variables = variables;
        this.root = root;
    }

    public ExecutionId getExecutionId() {
        return executionId;
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

    public <T> T getRoot() {
        //noinspection unchecked
        return (T) root;
    }

    public FragmentDefinition getFragment(String name) {
        return fragmentsByName.get(name);
    }

    public void addError(GraphQLError error) {
        this.errors.add(error);
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    public ExecutionStrategy getQueryStrategy() {
        return queryStrategy;
    }

    public ExecutionStrategy getMutationStrategy() {
        return mutationStrategy;
    }

    public ExecutionConstraints getExecutionConstraints() {
        return executionConstraints;
    }
}
