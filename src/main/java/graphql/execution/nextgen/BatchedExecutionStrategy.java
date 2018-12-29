package graphql.execution.nextgen;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.MergedField;
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

import static graphql.util.FpKit.flatList;
import static graphql.util.FpKit.map;
import static graphql.util.FpKit.mapEntries;
import static graphql.util.FpKit.transposeMatrix;

@Internal
public class BatchedExecutionStrategy implements ExecutionStrategy {

    ExecutionStepInfoFactory executionInfoFactory = new ExecutionStepInfoFactory();
    ValueFetcher valueFetcher = new ValueFetcher();
    ResultNodesCreator resultNodesCreator = new ResultNodesCreator();

    FetchedValueAnalyzer fetchedValueAnalyzer = new FetchedValueAnalyzer();
    ExecutionStrategyUtil util = new ExecutionStrategyUtil();


    public CompletableFuture<RootExecutionResultNode> execute(ExecutionContext executionContext, FieldSubSelection fieldSubSelection) {
        CompletableFuture<RootExecutionResultNode> rootCF = Async.each(util.fetchSubSelection(executionContext, fieldSubSelection))
                .thenApply(RootExecutionResultNode::new);

        return rootCF
                .thenCompose(rootNode -> {
                    ExecutionResultMultiZipper unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(rootNode);
                    return nextStep(executionContext, unresolvedNodes);
                })
                .thenApply(ExecutionResultMultiZipper::toRootNode)
                .thenApply(RootExecutionResultNode.class::cast);
    }


    private CompletableFuture<ExecutionResultMultiZipper> nextStep(ExecutionContext executionContext, ExecutionResultMultiZipper multizipper) {
        ExecutionResultMultiZipper nextUnresolvedNodes = ResultNodesUtil.getUnresolvedNodes(multizipper.toRootNode());
        if (nextUnresolvedNodes.getZippers().size() == 0) {
            return CompletableFuture.completedFuture(nextUnresolvedNodes);
        }
        List<ExecutionResultMultiZipper> groups = groupNodesIntoBatches(nextUnresolvedNodes);
        return nextStepImpl(executionContext, groups).thenCompose(next -> nextStep(executionContext, next));
    }

    // all multizipper have the same root
    private CompletableFuture<ExecutionResultMultiZipper> nextStepImpl(ExecutionContext executionContext, List<ExecutionResultMultiZipper> unresolvedNodes) {
        Assert.assertNotEmpty(unresolvedNodes, "unresolvedNodes can't be empty");
        ExecutionResultNode commonRoot = unresolvedNodes.get(0).getCommonRoot();

        CompletableFuture<List<List<ExecutionResultZipper>>> listListCF = Async.flatMap(unresolvedNodes,
                executionResultMultiZipper -> fetchAndAnalyze(executionContext, executionResultMultiZipper.getZippers()));

        return flatList(listListCF)
                .thenApply(zippers -> new ExecutionResultMultiZipper(commonRoot, zippers));

    }

    private List<ExecutionResultMultiZipper> groupNodesIntoBatches(ExecutionResultMultiZipper unresolvedZipper) {
        Map<Map<String, MergedField>, List<ExecutionResultZipper>> zipperBySubSelection = FpKit.groupingBy(unresolvedZipper.getZippers(),
                (executionResultZipper -> executionResultZipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection().getSubFields()));

        return mapEntries(zipperBySubSelection, (key, value) -> new ExecutionResultMultiZipper(unresolvedZipper.getCommonRoot(), value));
    }

    //constrain: all fieldSubSelections have the same fields
    private CompletableFuture<List<ExecutionResultZipper>> fetchAndAnalyze(ExecutionContext executionContext, List<ExecutionResultZipper> unresolvedNodes) {
        Assert.assertTrue(unresolvedNodes.size() > 0, "unresolvedNodes can't be empty");

        List<FieldSubSelection> fieldSubSelections = map(unresolvedNodes, zipper -> zipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection());
        List<Object> sources = map(fieldSubSelections, FieldSubSelection::getSource);

        // each field in the subSelection has n sources as input
        Map<String, MergedField> subFields = fieldSubSelections.get(0).getSubFields();

        List<CompletableFuture<List<FetchedValueAnalysis>>> fetchedValues = mapEntries(subFields, (name, mergedField) -> {

            List<ExecutionStepInfo> newExecutionStepInfos = map(fieldSubSelections,
                    executionResultNode -> executionInfoFactory.newExecutionStepInfoForSubField(executionContext, mergedField, executionResultNode.getExecutionStepInfo()));

            CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalyzis = valueFetcher
                    .fetchBatchedValues(executionContext, sources, mergedField, newExecutionStepInfos)
                    .thenApply(fetchValue -> analyseValues(executionContext, fetchValue, name, mergedField, newExecutionStepInfos));
            return fetchedValueAnalyzis;
        });

        return Async.each(fetchedValues).thenApply(fetchedValuesMatrix -> {
            List<ExecutionResultZipper> result = new ArrayList<>();
            List<List<FetchedValueAnalysis>> newChildsPerNode = transposeMatrix(fetchedValuesMatrix);

            for (int i = 0; i < newChildsPerNode.size(); i++) {
                ExecutionResultZipper unresolvedNodeZipper = unresolvedNodes.get(i);
                List<FetchedValueAnalysis> fetchedValuesForNode = newChildsPerNode.get(i);
                ExecutionResultZipper resolvedZipper = resolvedZipper(unresolvedNodeZipper, fetchedValuesForNode);
                result.add(resolvedZipper);
            }
            return result;
        });
    }

    private ExecutionResultZipper resolvedZipper(ExecutionResultZipper unresolvedNodeZipper, List<FetchedValueAnalysis> fetchedValuesForNode) {
        ObjectExecutionResultNode.UnresolvedObjectResultNode unresolvedNode = (ObjectExecutionResultNode.UnresolvedObjectResultNode) unresolvedNodeZipper.getCurNode();
        List<NamedResultNode> newChildren = util.fetchedValueAnalysisToNodes(fetchedValuesForNode);
        ObjectExecutionResultNode newNode = unresolvedNode.withChildren(newChildren);
        return unresolvedNodeZipper.withNode(newNode);
    }


    private List<FetchedValueAnalysis> analyseValues(ExecutionContext executionContext, List<FetchedValue> fetchedValues, String name, MergedField field, List<ExecutionStepInfo> executionInfos) {
        List<FetchedValueAnalysis> result = new ArrayList<>();
        for (int i = 0; i < fetchedValues.size(); i++) {
            FetchedValue fetchedValue = fetchedValues.get(i);
            ExecutionStepInfo executionStepInfo = executionInfos.get(i);
            FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(executionContext, fetchedValue, name, field, executionStepInfo);
            result.add(fetchedValueAnalysis);
        }
        return result;
    }
}
