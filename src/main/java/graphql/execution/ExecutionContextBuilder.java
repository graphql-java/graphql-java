package graphql.execution;

import graphql.GraphQLException;
import graphql.execution.instrumentation.Instrumentation;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class ExecutionContextBuilder {

    private ValuesResolver valuesResolver;
    private Instrumentation instrumentation;
    private ExecutionId executionId;

    public ExecutionContextBuilder(ValuesResolver valuesResolver, Instrumentation instrumentation) {
        this.valuesResolver = valuesResolver;
        this.instrumentation = instrumentation;
    }


    public ExecutionContextBuilder executionId(ExecutionId executionId) {
        this.executionId = executionId;
        return this;
    }

    public ExecutionContext build(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, Object context, Object root, Document document, String operationName, Map<String, Object> args) {
        // preconditions
        assertNotNull(executionId, "You must provide a query identifier");

        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        Map<String, OperationDefinition> operationsByName = new LinkedHashMap<>();

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
        if (operationName == null && operationsByName.size() > 1) {
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
        Map<String, Object> variableValues = valuesResolver.getVariableValues(graphQLSchema, operation.getVariableDefinitions(), args);

        return new ExecutionContext(
                instrumentation,
                executionId,
                graphQLSchema,
                queryStrategy,
                mutationStrategy,
                subscriptionStrategy,
                fragmentsByName,
                operation,
                variableValues,
                root,
                context);
    }
}
