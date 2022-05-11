package graphql.execution.nextgen;

import graphql.ExecutionResult;
import graphql.Internal;
import graphql.execution.ExecutionContext;

import java.util.concurrent.CompletableFuture;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public interface ExecutionStrategy {

    CompletableFuture<ExecutionResult> execute(ExecutionContext context);

}
