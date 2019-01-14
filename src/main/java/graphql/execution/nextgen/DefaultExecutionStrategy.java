package graphql.execution.nextgen;

import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.nextgen.result.ExecutionResultMultiZipper;
import graphql.execution.nextgen.result.ExecutionResultZipper;
import graphql.execution.nextgen.result.NamedResultNode;
import graphql.execution.nextgen.result.ObjectExecutionResultNode;
import graphql.execution.nextgen.result.ResultNodesUtil;
import graphql.execution.nextgen.result.RootExecutionResultNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.Async.each;
import static graphql.execution.Async.mapCompose;
import static graphql.util.FpKit.map;

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
    public CompletableFuture<RootExecutionResultNode> execute(ExecutionContext context, FieldSubSelection fieldSubSelection) {
        return resolveSubSelection(context, fieldSubSelection)
                .thenApply(RootExecutionResultNode::new);
    }

    private CompletableFuture<List<NamedResultNode>> resolveSubSelection(ExecutionContext executionContext, FieldSubSelection fieldSubSelection) {
        List<CompletableFuture<NamedResultNode>> namedNodesCFList =
                mapCompose(util.fetchSubSelection(executionContext, fieldSubSelection), node -> resolveAllChildNodes(executionContext, node));
        return each(namedNodesCFList);
    }

    private CompletableFuture<NamedResultNode> resolveAllChildNodes(ExecutionContext context, NamedResultNode namedResultNode) {
        ExecutionResultMultiZipper unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(namedResultNode.getNode());
        List<CompletableFuture<ExecutionResultZipper>> resolvedNodes = map(unresolvedNodes.getZippers(), unresolvedNode -> resolveNode(context, unresolvedNode));
        return resolvedNodesToResultNode(namedResultNode, unresolvedNodes, resolvedNodes);
    }

    private CompletableFuture<ExecutionResultZipper> resolveNode(ExecutionContext executionContext, ExecutionResultZipper unresolvedNode) {
        FetchedValueAnalysis fetchedValueAnalysis = unresolvedNode.getCurNode().getFetchedValueAnalysis();
        FieldSubSelection fieldSubSelection = util.createFieldSubSelection(executionContext, fetchedValueAnalysis);
        return resolveSubSelection(executionContext, fieldSubSelection)
                .thenApply(resolvedChildMap -> unresolvedNode.withNode(new ObjectExecutionResultNode(fetchedValueAnalysis, resolvedChildMap)));
    }

    private CompletableFuture<NamedResultNode> resolvedNodesToResultNode(NamedResultNode namedResultNode,
                                                                         ExecutionResultMultiZipper unresolvedNodes,
                                                                         List<CompletableFuture<ExecutionResultZipper>> resolvedNodes) {
        return each(resolvedNodes)
                .thenApply(unresolvedNodes::withZippers)
                .thenApply(ExecutionResultMultiZipper::toRootNode)
                .thenApply(namedResultNode::withNode);
    }


}
