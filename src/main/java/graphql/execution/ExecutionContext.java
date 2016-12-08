package graphql.execution;


import graphql.GraphQLError;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.*;

public class ExecutionContext {

    private GraphQLSchema graphQLSchema;
    private ExecutionStrategy queryStrategy;
    private ExecutionStrategy mutationStrategy;
    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<String, FragmentDefinition>();
    private OperationDefinition operationDefinition;
    private Map<String, Object> variables = new LinkedHashMap<String, Object>();
    private Object root;
    private List<GraphQLError> errors = Collections.synchronizedList(new ArrayList<GraphQLError>());

    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    public void setGraphQLSchema(GraphQLSchema graphQLSchema) {
        this.graphQLSchema = graphQLSchema;
    }

    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    public void setFragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
        this.fragmentsByName = fragmentsByName;
    }

    public OperationDefinition getOperationDefinition() {
        return operationDefinition;
    }

    public void setOperationDefinition(OperationDefinition operationDefinition) {
        this.operationDefinition = operationDefinition;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public Object getRoot() {
        return root;
    }

    public void setRoot(Object root) {
        this.root = root;
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

    public void setQueryStrategy(ExecutionStrategy queryStrategy) {
        this.queryStrategy = queryStrategy;
    }

    public ExecutionStrategy getMutationStrategy() {
        return mutationStrategy;
    }

    public void setMutationStrategy(ExecutionStrategy mutationStrategy) {
        this.mutationStrategy = mutationStrategy;
    }
}
