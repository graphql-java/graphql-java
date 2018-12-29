package graphql.execution.nextgen;

import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.nextgen.result.ExecutionResultMultiZipper;
import graphql.execution.nextgen.result.ExecutionResultZipper;
import graphql.execution.nextgen.result.NamedResultNode;
import graphql.execution.nextgen.result.ObjectExecutionResultNode;
import graphql.execution.nextgen.result.ResultNodesUtil;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Internal
public class DefaultExecutionStrategy implements ExecutionStrategy {

    ExecutionStrategyUtil util = new ExecutionStrategyUtil();


    /*
     * the fundamental algorithm is:
     * - fetch sub selection and analyze it
     * - convert the fetched value analysis into result node
     * - get all unresolved result nodes and resolve the sub selection (start again recursively)
     */
    @Override
    public CompletableFuture<ObjectExecutionResultNode.RootExecutionResultNode> execute(ExecutionContext context, FieldSubSelection fieldSubSelection) {
        return resolveSubSelection(context, fieldSubSelection)
                .thenApply(ObjectExecutionResultNode.RootExecutionResultNode::new);
    }

    // recursive entry point
    private CompletableFuture<List<NamedResultNode>> resolveSubSelection(ExecutionContext executionContext, FieldSubSelection fieldSubSelection) {
        List<CompletableFuture<NamedResultNode>> result = util.fetchSubSelection(executionContext, fieldSubSelection)
                .stream().map(namedResultNodeCF -> namedResultNodeCF.thenCompose(node -> resolveNode(executionContext, node))).collect(toList());
        return Async.each(result);
    }

    // ----------- fetching subSelection into ResultNode

    // ----------- get all unresolved Nodes and recursively resolves them
    // this method is actually an async transformer of specific child nodes
    private CompletableFuture<NamedResultNode> resolveNode(ExecutionContext context, NamedResultNode namedResultNode) {
        // can be empty
        ExecutionResultMultiZipper unresolvedMultiZipper = ResultNodesUtil.getUnresolvedNodes(namedResultNode.getNode());
        // must be a unresolved Node
        List<CompletableFuture<ExecutionResultZipper>> cfList = unresolvedMultiZipper
                .getZippers()
                .stream()
                .map(zipper -> resolveUnresolvedNode(context, zipper))
                .collect(Collectors.toList());
        return Async
                .each(cfList)
                .thenApply(unresolvedMultiZipper::withZippers)
                .thenApply(ExecutionResultMultiZipper::toRootNode)
                .thenApply(namedResultNode::withNode);
    }

    // recursive call back to resolveSubSelection
    private CompletableFuture<ExecutionResultZipper> resolveUnresolvedNode(ExecutionContext executionContext, ExecutionResultZipper unresolvedNodeZipper) {
        FetchedValueAnalysis fetchedValueAnalysis = unresolvedNodeZipper.getCurNode().getFetchedValueAnalysis();
        return resolveSubSelection(executionContext, fetchedValueAnalysis.getFieldSubSelection())
                .thenApply(resolvedChildMap -> unresolvedNodeZipper.withNode(new ObjectExecutionResultNode(fetchedValueAnalysis, resolvedChildMap)));
    }


}
