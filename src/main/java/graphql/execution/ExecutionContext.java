package graphql.execution;


import graphql.GraphQLError;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ExecutionContext {

    private final GraphQLSchema graphQLSchema;
    private final ExecutionId executionId;
    private final ExecutionStrategy executionStrategy;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final OperationDefinition operationDefinition;
    private final Map<String, Object> variables;
    private final Object root;
    private final List<GraphQLError> errors = new CopyOnWriteArrayList<GraphQLError>();

    public ExecutionContext(GraphQLSchema graphQLSchema, ExecutionId executionId, ExecutionStrategy executionStrategy, Map<String, FragmentDefinition> fragmentsByName, OperationDefinition operationDefinition, Map<String, Object> variables, Object root) {
        this.graphQLSchema = graphQLSchema;
        this.executionId = executionId;
        this.executionStrategy = executionStrategy;
        this.fragmentsByName = fragmentsByName;
        this.operationDefinition = operationDefinition;
        this.variables = variables;
        this.root = root;
    }

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public ExecutionId getExecutionId() {
        return executionId;
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

    public Object getRoot() {
        return root;
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

    public ExecutionStrategy getExecutionStrategy() {
        return executionStrategy;
    }

}
