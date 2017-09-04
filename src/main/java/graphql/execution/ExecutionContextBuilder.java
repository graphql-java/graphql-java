package graphql.execution;

import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.HashMap;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class ExecutionContextBuilder {

    private Instrumentation instrumentation;
    private ExecutionId executionId;
    private InstrumentationState instrumentationState;
    private GraphQLSchema graphQLSchema;
    private ExecutionStrategy queryStrategy;
    private ExecutionStrategy mutationStrategy;
    private ExecutionStrategy subscriptionStrategy;
    private Object context;
    private Object root;
    private OperationDefinition operationDefinition;
    private Map<String, Object> variables = new HashMap<>();
    private Map<String, FragmentDefinition> fragmentsByName = new HashMap<>();

    public ExecutionContextBuilder instrumentation(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
        return this;
    }

    public ExecutionContextBuilder instrumentationState(InstrumentationState instrumentationState) {
        this.instrumentationState = instrumentationState;
        return this;
    }

    public ExecutionContextBuilder executionId(ExecutionId executionId) {
        this.executionId = executionId;
        return this;
    }

    public ExecutionContextBuilder graphQLSchema(GraphQLSchema graphQLSchema) {
        this.graphQLSchema = graphQLSchema;
        return this;
    }

    public ExecutionContextBuilder queryStrategy(ExecutionStrategy queryStrategy) {
        this.queryStrategy = queryStrategy;
        return this;
    }

    public ExecutionContextBuilder mutationStrategy(ExecutionStrategy mutationStrategy) {
        this.mutationStrategy = mutationStrategy;
        return this;
    }

    public ExecutionContextBuilder subscriptionStrategy(ExecutionStrategy subscriptionStrategy) {
        this.subscriptionStrategy = subscriptionStrategy;
        return this;
    }

    public ExecutionContextBuilder context(Object context) {
        this.context = context;
        return this;
    }

    public ExecutionContextBuilder root(Object root) {
        this.root = root;
        return this;
    }

    public ExecutionContextBuilder variables(Map<String, Object> variables) {
        this.variables = variables;
        return this;
    }

    public ExecutionContextBuilder fragmentsByName(Map<String, FragmentDefinition> fragmentsByName) {
        this.fragmentsByName = fragmentsByName;
        return this;
    }

    public ExecutionContextBuilder operationDefinition(OperationDefinition operationDefinition) {
        this.operationDefinition = operationDefinition;
        return this;
    }

    public ExecutionContext build() {
        // preconditions
        assertNotNull(executionId, "You must provide a query identifier");


        return new ExecutionContext(
                instrumentation,
                executionId,
                graphQLSchema,
                instrumentationState,
                queryStrategy,
                mutationStrategy,
                subscriptionStrategy,
                fragmentsByName,
                operationDefinition,
                variables,
                context,
                root);
    }
}
