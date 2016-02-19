package graphql.execution;


import graphql.GraphQLError;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>ExecutionContext class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ExecutionContext {

    private GraphQLSchema graphQLSchema;
    private ExecutionStrategy executionStrategy;
    private Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
    private OperationDefinition operationDefinition;
    private Map<String, Object> variables = new LinkedHashMap<>();
    private Object root;
    private List<GraphQLError> errors = new ArrayList<>();

    /**
     * <p>Getter for the field <code>graphQLSchema</code>.</p>
     *
     * @return a {@link graphql.schema.GraphQLSchema} object.
     */
    public GraphQLSchema getGraphQLSchema() {
        return graphQLSchema;
    }

    /**
     * <p>Setter for the field <code>graphQLSchema</code>.</p>
     *
     * @param graphQLSchema a {@link graphql.schema.GraphQLSchema} object.
     */
    public void setGraphQLSchema(GraphQLSchema graphQLSchema) {
        this.graphQLSchema = graphQLSchema;
    }

    /**
     * <p>Getter for the field <code>fragmentsByName</code>.</p>
     *
     * @return a {@link java.util.Map} object.
     */
    public Map<String, FragmentDefinition> getFragmentsByName() {
        return fragmentsByName;
    }

    /**
     * <p>Setter for the field <code>fragmentsByName</code>.</p>
     *
     * @param fragmentsByName a {@link java.util.Map} object.
     */
    public void setFragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
        this.fragmentsByName = fragmentsByName;
    }

    /**
     * <p>Getter for the field <code>operationDefinition</code>.</p>
     *
     * @return a {@link graphql.language.OperationDefinition} object.
     */
    public OperationDefinition getOperationDefinition() {
        return operationDefinition;
    }

    /**
     * <p>Setter for the field <code>operationDefinition</code>.</p>
     *
     * @param operationDefinition a {@link graphql.language.OperationDefinition} object.
     */
    public void setOperationDefinition(OperationDefinition operationDefinition) {
        this.operationDefinition = operationDefinition;
    }

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return a {@link java.util.Map} object.
     */
    public Map<String, Object> getVariables() {
        return variables;
    }

    /**
     * <p>Setter for the field <code>variables</code>.</p>
     *
     * @param variables a {@link java.util.Map} object.
     */
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    /**
     * <p>Getter for the field <code>root</code>.</p>
     *
     * @return a {@link java.lang.Object} object.
     */
    public Object getRoot() {
        return root;
    }

    /**
     * <p>Setter for the field <code>root</code>.</p>
     *
     * @param root a {@link java.lang.Object} object.
     */
    public void setRoot(Object root) {
        this.root = root;
    }

    /**
     * <p>getFragment.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @return a {@link graphql.language.FragmentDefinition} object.
     */
    public FragmentDefinition getFragment(String name) {
        return fragmentsByName.get(name);
    }

    /**
     * <p>addError.</p>
     *
     * @param error a {@link graphql.GraphQLError} object.
     */
    public void addError(GraphQLError error) {
        this.errors.add(error);
    }

    /**
     * <p>Getter for the field <code>errors</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLError> getErrors(){
        return errors;
    }

    /**
     * <p>Getter for the field <code>executionStrategy</code>.</p>
     *
     * @return a {@link graphql.execution.ExecutionStrategy} object.
     */
    public ExecutionStrategy getExecutionStrategy() {
        return executionStrategy;
    }

    /**
     * <p>Setter for the field <code>executionStrategy</code>.</p>
     *
     * @param executionStrategy a {@link graphql.execution.ExecutionStrategy} object.
     */
    public void setExecutionStrategy(ExecutionStrategy executionStrategy) {
        this.executionStrategy = executionStrategy;
    }
}
