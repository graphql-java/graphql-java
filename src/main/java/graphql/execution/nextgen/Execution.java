package graphql.execution.nextgen;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionId;
import graphql.execution.nextgen.result.ResultNodesUtil;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.concurrent.CompletableFuture;

@Internal
public class Execution {

    ExecutionHelper executionHelper = new ExecutionHelper();

    public CompletableFuture<ExecutionResult> execute(Class<? extends ExecutionStrategy> executionStrategy,
                                                      Document document,
                                                      GraphQLSchema graphQLSchema,
                                                      ExecutionId executionId,
                                                      ExecutionInput executionInput) {
        ExecutionHelper.ExecutionData executionData;
        try {
            executionData = executionHelper.createExecutionData(document, graphQLSchema, executionId, executionInput);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                return CompletableFuture.completedFuture(new ExecutionResultImpl((GraphQLError) rte));
            }
            return Async.exceptionallyCompletedFuture(rte);
        }

        try {
            return executionStrategy
                    .getConstructor()
                    .newInstance()
                    .execute(executionData.executionContext, executionData.fieldSubSelection)
                    .thenApply(ResultNodesUtil::toExecutionResult);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
