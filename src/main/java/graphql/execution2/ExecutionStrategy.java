package graphql.execution2;

import graphql.Internal;
import graphql.execution2.result.ObjectExecutionResultNode.RootExecutionResultNode;

import java.util.concurrent.CompletableFuture;

@Internal
public interface ExecutionStrategy {

    CompletableFuture<RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection);

}
