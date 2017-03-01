package graphql.execution;

import graphql.ExecutionResult;
import graphql.execution.async.AsyncExecutionStrategy;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class AsyncExecution extends Execution {

    public AsyncExecution(ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy) {
        super(queryStrategy, mutationStrategy);
    }

    public CompletionStage<ExecutionResult> executeAsync(GraphQLSchema graphQLSchema, Object root, Document document, String operationName, Map<String, Object> args) {
        ExecutionContextBuilder executionContextBuilder = new ExecutionContextBuilder(new ValuesResolver());
        ExecutionContext executionContext = executionContextBuilder
          .executionId(ExecutionId.generate())
          .build(graphQLSchema, queryStrategy, mutationStrategy, root, document, operationName, args);
        return executeOperationAsync(executionContext, root, executionContext.getOperationDefinition());
    }

    private CompletionStage<ExecutionResult> executeOperationAsync(
      ExecutionContext executionContext,
      Object root,
      OperationDefinition operationDefinition) {
        GraphQLObjectType operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);

        Map<String, List<Field>> fields = new LinkedHashMap<String, List<Field>>();
        fieldCollector.collectFields(executionContext, operationRootType, operationDefinition.getSelectionSet(), new ArrayList<String>(), fields);

        if (operationDefinition.getOperation() == OperationDefinition.Operation.MUTATION) {
            return ((AsyncExecutionStrategy) mutationStrategy).executeAsync(executionContext, operationRootType, root, fields);
        } else {
            return ((AsyncExecutionStrategy) queryStrategy).executeAsync(executionContext, operationRootType, root, fields);
        }
    }
}
