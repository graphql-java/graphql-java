package graphql.execution2;

import graphql.PublicSpi;
import graphql.execution2.result.ObjectExecutionResultNode.RootExecutionResultNode;

import java.util.concurrent.CompletableFuture;

@PublicSpi
public interface ExecutionStrategy {

    CompletableFuture<RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection);

}
