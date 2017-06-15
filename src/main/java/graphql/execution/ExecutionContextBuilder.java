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
    private GraphQLSchema graphQLSchema;
    private ExecutionStrategy queryStrategy;
    private ExecutionStrategy mutationStrategy;
    private ExecutionStrategy subscriptionStrategy;
    private Object context;
    private Object root;
    private Document document;
    private String operationName;
    private Map<String, Object> args;

    public ExecutionContextBuilder valuesResolver(final ValuesResolver valuesResolver) {
        this.valuesResolver = valuesResolver;
        return this;
    }

    public ExecutionContextBuilder instrumentation(final Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
        return this;
    }

    public ExecutionContextBuilder executionId(ExecutionId executionId) {
        this.executionId = executionId;
        return this;
    }

    public ExecutionContextBuilder graphQLSchema(final GraphQLSchema graphQLSchema) {
        this.graphQLSchema = graphQLSchema;
        return this;
    }

    public ExecutionContextBuilder queryStrategy(final ExecutionStrategy queryStrategy) {
        this.queryStrategy = queryStrategy;
        return this;
    }

    public ExecutionContextBuilder mutationStrategy(final ExecutionStrategy mutationStrategy) {
        this.mutationStrategy = mutationStrategy;
        return this;
    }

    public ExecutionContextBuilder subscriptionStrategy(final ExecutionStrategy subscriptionStrategy) {
        this.subscriptionStrategy = subscriptionStrategy;
        return this;
    }

    public ExecutionContextBuilder context(final Object context) {
        this.context = context;
        return this;
    }

    public ExecutionContextBuilder root(final Object root) {
        this.root = root;
        return this;
    }

    public ExecutionContextBuilder document(final Document document) {
        this.document = document;
        return this;
    }

    public ExecutionContextBuilder operationName(final String operationName) {
        this.operationName = operationName;
        return this;
    }

    public ExecutionContextBuilder args(final Map<String, Object> args) {
        this.args = args;
        return this;
    }

    public ExecutionContext build() {
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
