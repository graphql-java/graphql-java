package graphql.execution;

import graphql.GraphQLException;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>ExecutionContextBuilder class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ExecutionContextBuilder {

    private ValuesResolver valuesResolver;

    /**
     * <p>Constructor for ExecutionContextBuilder.</p>
     *
     * @param valuesResolver a {@link graphql.execution.ValuesResolver} object.
     */
    public ExecutionContextBuilder(ValuesResolver valuesResolver) {
        this.valuesResolver = valuesResolver;
    }

    /**
     * <p>build.</p>
     *
     * @param graphQLSchema a {@link graphql.schema.GraphQLSchema} object.
     * @param executionStrategy a {@link graphql.execution.ExecutionStrategy} object.
     * @param root a {@link java.lang.Object} object.
     * @param document a {@link graphql.language.Document} object.
     * @param operationName a {@link java.lang.String} object.
     * @param args a {@link java.util.Map} object.
     * @return a {@link graphql.execution.ExecutionContext} object.
     */
    public ExecutionContext build(GraphQLSchema graphQLSchema, ExecutionStrategy executionStrategy, Object root, Document document, String operationName, Map<String, Object> args) {
        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<String, FragmentDefinition>();
        Map<String, OperationDefinition> operationsByName = new LinkedHashMap<String, OperationDefinition>();

        for (Definition definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition) {
                OperationDefinition operationDefinition = (OperationDefinition) definition;
                operationsByName.put(operationDefinition.getName(), operationDefinition);
            }
            if (definition instanceof FragmentDefinition) {
                FragmentDefinition fragmentDefinition = (FragmentDefinition) definition;
                fragmentsByName.put(fragmentDefinition.getName(), fragmentDefinition);
            }
        }
        if (operationName != null && operationsByName.size() > 1) {
            throw new GraphQLException("missing operation name");
        }
        OperationDefinition operation;

        if (operationName == null) {
            operation = operationsByName.values().iterator().next();
        } else {
            operation = operationsByName.get(operationName);
        }
        if (operation == null) {
            throw new GraphQLException();
        }

        ExecutionContext executionContext = new ExecutionContext();
        executionContext.setGraphQLSchema(graphQLSchema);
        executionContext.setExecutionStrategy(executionStrategy);
        executionContext.setOperationDefinition(operation);
        executionContext.setRoot(root);
        executionContext.setFragmentsByName(fragmentsByName);
        Map<String, Object> variableValues = valuesResolver.getVariableValues(graphQLSchema, operation.getVariableDefinitions(), args);
        executionContext.setVariables(variableValues);
        return executionContext;
    }
}
