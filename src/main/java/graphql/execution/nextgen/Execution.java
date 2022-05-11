package graphql.execution.nextgen;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionId;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.concurrent.CompletableFuture;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public class Execution {

    ExecutionHelper executionHelper = new ExecutionHelper();

    public CompletableFuture<ExecutionResult> execute(ExecutionStrategy executionStrategy,
                                                      Document document,
                                                      GraphQLSchema graphQLSchema,
                                                      ExecutionId executionId,
                                                      ExecutionInput executionInput,
                                                      InstrumentationState instrumentationState) {
        ExecutionHelper.ExecutionData executionData;
        try {
            executionData = executionHelper.createExecutionData(document, graphQLSchema, executionId, executionInput, instrumentationState);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                return CompletableFuture.completedFuture(new ExecutionResultImpl((GraphQLError) rte));
            }
            return Async.exceptionallyCompletedFuture(rte);
        }

        try {
            return executionStrategy
                    .execute(executionData.executionContext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
