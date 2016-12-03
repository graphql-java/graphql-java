package graphql;

import graphql.execution.ExecutionStrategy;
import graphql.schema.GraphQLSchema;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class GraphQLAsync extends GraphQL {

    public GraphQLAsync(GraphQLSchema graphQLSchema) {
        super(graphQLSchema);
    }

    public GraphQLAsync(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy) {
        super(graphQLSchema, queryStrategy);
    }

    public GraphQLAsync(GraphQLSchema graphQLSchema, ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        super(graphQLSchema, queryStrategy, mutationStrategy);
    }

    public CompletionStage<ExecutionResult> executeAsync(String requestString) {
        return executeAsync(requestString, null);

    }

    public CompletionStage<ExecutionResult> executeAsync(String requestString, Object context) {
        return executeAsync(requestString, context, Collections.emptyMap());

    }

    public CompletionStage<ExecutionResult> executeAsync(String requestString, String operationName, Object context) {
        return executeAsync(requestString, operationName, context, Collections.emptyMap());

    }

    public CompletionStage<ExecutionResult> executeAsync(String requestString, Object context, Map<String, Object> arguments) {
        return executeAsync(requestString, null, context, arguments);

    }

    @SuppressWarnings("unchecked")
    public CompletionStage<ExecutionResult> executeAsync(String requestString, String operationName, Object context, Map<String, Object> arguments) {
        ExecutionResult result = execute(requestString, operationName, context, arguments);
        Object data1 = result.getData();
        if (data1 instanceof CompletionStage) {
            return ((CompletionStage<Map<String, Object>>) data1).thenApply(data -> new ExecutionResultImpl(data, result.getErrors()));
        }
        return completedFuture(result);
    }
}
