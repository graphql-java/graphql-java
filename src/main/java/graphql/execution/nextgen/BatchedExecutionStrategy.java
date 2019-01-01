package graphql.execution.nextgen;

import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.nextgen.result.ExecutionResultMultiZipper;
import graphql.execution.nextgen.result.ExecutionResultNode;
import graphql.execution.nextgen.result.ExecutionResultZipper;
import graphql.execution.nextgen.result.NamedResultNode;
import graphql.execution.nextgen.result.ObjectExecutionResultNode;
import graphql.execution.nextgen.result.ObjectExecutionResultNode.RootExecutionResultNode;
import graphql.execution.nextgen.result.ResultNodesUtil;
import graphql.util.FpKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertTrue;
import static graphql.util.FpKit.flatList;
import static graphql.util.FpKit.map;
import static graphql.util.FpKit.mapEntries;
import static graphql.util.FpKit.transposeMatrix;
import static java.util.concurrent.CompletableFuture.completedFuture;

@Internal
public class BatchedExecutionStrategy implements ExecutionStrategy {

    ExecutionStepInfoFactory executionInfoFactory = new ExecutionStepInfoFactory();
    ValueFetcher valueFetcher = new ValueFetcher();

    FetchedValueAnalyzer fetchedValueAnalyzer = new FetchedValueAnalyzer();
    ExecutionStrategyUtil util = new ExecutionStrategyUtil();


    @Override
    public CompletableFuture<RootExecutionResultNode> execute(ExecutionContext executionContext, FieldSubSelection fieldSubSelection) {
        CompletableFuture<RootExecutionResultNode> rootCF = Async.each(util.fetchSubSelection(executionContext, fieldSubSelection))
                .thenApply(RootExecutionResultNode::new);

        return rootCF.thenCompose(rootNode -> {
            ExecutionResultMultiZipper unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(rootNode);
            return nextStep(executionContext, unresolvedNodes);
        })
                .thenApply(ExecutionResultMultiZipper::toRootNode)
                .thenApply(RootExecutionResultNode.class::cast);
    }


    private CompletableFuture<ExecutionResultMultiZipper> nextStep(ExecutionContext executionContext, ExecutionResultMultiZipper multizipper) {
        ExecutionResultMultiZipper nextUnresolvedNodes = ResultNodesUtil.getUnresolvedNodes(multizipper.toRootNode());
        if (nextUnresolvedNodes.getZippers().size() == 0) {
            return completedFuture(nextUnresolvedNodes);
        }
        List<ExecutionResultMultiZipper> groups = groupNodesIntoBatches(nextUnresolvedNodes);
        return resolveNodes(executionContext, groups).thenCompose(next -> nextStep(executionContext, next));
    }

    // all multizipper have the same root
    private CompletableFuture<ExecutionResultMultiZipper> resolveNodes(ExecutionContext executionContext, List<ExecutionResultMultiZipper> unresolvedNodes) {
        assertNotEmpty(unresolvedNodes, "unresolvedNodes can't be empty");
        ExecutionResultNode commonRoot = unresolvedNodes.get(0).getCommonRoot();
        CompletableFuture<List<List<ExecutionResultZipper>>> listListCF = Async.flatMap(unresolvedNodes,
                executionResultMultiZipper -> fetchAndAnalyze(executionContext, executionResultMultiZipper.getZippers()));

        return flatList(listListCF).thenApply(zippers -> new ExecutionResultMultiZipper(commonRoot, zippers));
    }

    private List<ExecutionResultMultiZipper> groupNodesIntoBatches(ExecutionResultMultiZipper unresolvedZipper) {
        Map<MergedField, List<ExecutionResultZipper>> zipperBySubSelection = FpKit.groupingBy(unresolvedZipper.getZippers(),
                (executionResultZipper -> executionResultZipper.getCurNode().getMergedField()));
        return mapEntries(zipperBySubSelection, (key, value) -> new ExecutionResultMultiZipper(unresolvedZipper.getCommonRoot(), value));
    }

    private CompletableFuture<List<ExecutionResultZipper>> fetchAndAnalyze(ExecutionContext executionContext, List<ExecutionResultZipper> unresolvedNodes) {
        assertTrue(unresolvedNodes.size() > 0, "unresolvedNodes can't be empty");

        List<FieldSubSelection> fieldSubSelections = map(unresolvedNodes,
                node -> util.createFieldSubSelection(executionContext, node.getCurNode().getFetchedValueAnalysis()));

        //constrain: all fieldSubSelections have the same mergedSelectionSet
        MergedSelectionSet mergedSelectionSet = fieldSubSelections.get(0).getMergedSelectionSet();

        List<CompletableFuture<List<FetchedValueAnalysis>>> fetchedValues = batchFetchForEachSubField(executionContext, fieldSubSelections, mergedSelectionSet);

        return mapBatchedResultsBack(unresolvedNodes, fetchedValues);
    }

    private CompletableFuture<List<ExecutionResultZipper>> mapBatchedResultsBack(List<ExecutionResultZipper> unresolvedNodes, List<CompletableFuture<List<FetchedValueAnalysis>>> fetchedValues) {
        return Async.each(fetchedValues).thenApply(fetchedValuesMatrix -> {
            List<ExecutionResultZipper> result = new ArrayList<>();
            List<List<FetchedValueAnalysis>> newChildsPerNode = transposeMatrix(fetchedValuesMatrix);

            for (int i = 0; i < newChildsPerNode.size(); i++) {
                ExecutionResultZipper unresolvedNodeZipper = unresolvedNodes.get(i);
                List<FetchedValueAnalysis> fetchedValuesForNode = newChildsPerNode.get(i);
                ExecutionResultZipper resolvedZipper = resolveZipper(unresolvedNodeZipper, fetchedValuesForNode);
                result.add(resolvedZipper);
            }
            return result;
        });
    }

    private List<CompletableFuture<List<FetchedValueAnalysis>>> batchFetchForEachSubField(ExecutionContext executionContext,
                                                                                          List<FieldSubSelection> fieldSubSelections,
                                                                                          MergedSelectionSet mergedSelectionSet) {
        List<Object> sources = map(fieldSubSelections, FieldSubSelection::getSource);
        return mapEntries(mergedSelectionSet.getSubFields(), (name, mergedField) -> {
            List<ExecutionStepInfo> newExecutionStepInfos = newExecutionInfos(executionContext, fieldSubSelections, mergedField);
            return valueFetcher
                    .fetchBatchedValues(executionContext, sources, mergedField, newExecutionStepInfos)
                    .thenApply(fetchValue -> analyseValues(executionContext, fetchValue, newExecutionStepInfos));
        });
    }

    private List<ExecutionStepInfo> newExecutionInfos(ExecutionContext executionContext, List<FieldSubSelection> fieldSubSelections, MergedField mergedField) {
        return map(fieldSubSelections,
                subSelection -> executionInfoFactory.newExecutionStepInfoForSubField(executionContext, mergedField, subSelection.getExecutionStepInfo()));
    }

    private ExecutionResultZipper resolveZipper(ExecutionResultZipper unresolvedNodeZipper, List<FetchedValueAnalysis> fetchedValuesForNode) {
        ObjectExecutionResultNode.UnresolvedObjectResultNode unresolvedNode = (ObjectExecutionResultNode.UnresolvedObjectResultNode) unresolvedNodeZipper.getCurNode();
        List<NamedResultNode> newChildren = util.fetchedValueAnalysisToNodes(fetchedValuesForNode);
        ObjectExecutionResultNode newNode = unresolvedNode.withChildren(newChildren);
        return unresolvedNodeZipper.withNode(newNode);
    }


    private List<FetchedValueAnalysis> analyseValues(ExecutionContext executionContext, List<FetchedValue> fetchedValues, List<ExecutionStepInfo> executionInfos) {
        List<FetchedValueAnalysis> result = new ArrayList<>();
        for (int i = 0; i < fetchedValues.size(); i++) {
            FetchedValue fetchedValue = fetchedValues.get(i);
            ExecutionStepInfo executionStepInfo = executionInfos.get(i);
            FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(executionContext, fetchedValue, executionStepInfo);
            result.add(fetchedValueAnalysis);
        }
        return result;
    }
}
