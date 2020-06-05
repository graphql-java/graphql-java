package graphql.execution.nextgen;

import graphql.ExecutionResult;
import graphql.Internal;
import graphql.execution.ExecutionContext;

import java.util.concurrent.CompletableFuture;

@Internal
public interface ExecutionStrategy {

    CompletableFuture<ExecutionResult> execute(ExecutionContext context);

}
