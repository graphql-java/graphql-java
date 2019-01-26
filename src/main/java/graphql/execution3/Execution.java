/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.execution3;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import static graphql.execution3.ExecutionPlan.newExecutionPlanBuilder;

/**
 *
 * @author gkesler
 */
public class Execution {
    public CompletableFuture<ExecutionResult> execute (Class<? extends ExecutionStrategy> strategyClass, Document document, 
            GraphQLSchema schema, ExecutionId executionId, ExecutionInput executionInput) {
        return execute(executionContext -> newExecutionStrategy(strategyClass, executionContext), document, schema, executionId, executionInput);
    }

    public CompletableFuture<ExecutionResult> execute (Function<? super ExecutionContext, ? extends ExecutionStrategy> strategyCreator, Document document, 
            GraphQLSchema schema, ExecutionId executionId, ExecutionInput executionInput) {
        try {
            return doExecute(strategyCreator, document, schema, executionId, executionInput);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError)
                return CompletableFuture.completedFuture(new ExecutionResultImpl((GraphQLError) rte));

            return Async.exceptionallyCompletedFuture(rte);
        }
    }
    
    private CompletableFuture<ExecutionResult> doExecute (Function<? super ExecutionContext, ? extends ExecutionStrategy> strategyCreator, Document document, 
            GraphQLSchema schema, ExecutionId executionId, ExecutionInput executionInput) {
        ExecutionPlan executionPlan = newExecutionPlanBuilder()
            .schema(schema)
            .document(document)
            .operation(executionInput.getOperationName())
            .variables(executionInput.getVariables())
            .build();
        
        ExecutionContext executionContext = executionPlan
            .newExecutionContextBuilder(executionInput.getOperationName())
            .root(executionInput.getRoot())
            .context(executionInput.getContext())
            .dataLoaderRegistry(executionInput.getDataLoaderRegistry())
            .executionId(executionId)
            .build();
        
        return strategyCreator
            .apply(executionContext)
            .execute(executionPlan);
    }
    
    private static ExecutionStrategy newExecutionStrategy (Class<? extends ExecutionStrategy> strategyClass, ExecutionContext executionContext) {
        try {
            return strategyClass
                    .getConstructor(ExecutionContext.class)
                    .newInstance(executionContext);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
