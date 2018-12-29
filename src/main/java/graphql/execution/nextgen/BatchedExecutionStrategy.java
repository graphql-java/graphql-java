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
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Internal
public class BatchedExecutionStrategy implements ExecutionStrategy {

    ExecutionStepInfoFactory executionInfoFactory = new ExecutionStepInfoFactory();
    ValueFetcher valueFetcher = new ValueFetcher();
    ResultNodesCreator resultNodesCreator = new ResultNodesCreator();

    FetchedValueAnalyzer fetchedValueAnalyzer = new FetchedValueAnalyzer();
    ExecutionStrategyUtil util = new ExecutionStrategyUtil();


    public CompletableFuture<RootExecutionResultNode> execute(ExecutionContext executionContext, FieldSubSelection fieldSubSelection) {
        CompletableFuture<RootExecutionResultNode> rootCF = Async.each(util.fetchSubSelection(executionContext, fieldSubSelection)).thenApply(RootExecutionResultNode::new);

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

        return FpKit.flatList(listListCF)
                .thenApply(zippers -> new ExecutionResultMultiZipper(commonRoot, zippers));

    }

    private List<ExecutionResultMultiZipper> groupNodesIntoBatches(ExecutionResultMultiZipper unresolvedZipper) {
        Map<Map<String, MergedField>, List<ExecutionResultZipper>> zipperBySubSelection = unresolvedZipper.getZippers().stream()
                .collect(groupingBy(executionResultZipper -> executionResultZipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection().getSubFields()));

        return zipperBySubSelection
                .entrySet()
                .stream()
                .map(entry -> new ExecutionResultMultiZipper(unresolvedZipper.getCommonRoot(), entry.getValue()))
                .collect(Collectors.toList());
    }

    //constrain: all fieldSubSelections have the same fields
    private CompletableFuture<List<ExecutionResultZipper>> fetchAndAnalyze(ExecutionContext executionContext, List<ExecutionResultZipper> unresolvedNodes) {
        Assert.assertTrue(unresolvedNodes.size() > 0, "unresolvedNodes can't be empty");

        List<FieldSubSelection> fieldSubSelections = unresolvedNodes.stream()
                .map(zipper -> zipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection())
                .collect(Collectors.toList());
        List<Object> sources = fieldSubSelections.stream().map(fieldSubSelection -> fieldSubSelection.getSource()).collect(Collectors.toList());

        // each field in the subSelection has n sources as input
        List<CompletableFuture<List<FetchedValueAnalysis>>> fetchedValues = fieldSubSelections
                .get(0)
                .getSubFields()
                .entrySet()
                .stream()
                .map(entry -> {
                    MergedField sameFields = entry.getValue();
                    String name = entry.getKey();

                    List<ExecutionStepInfo> newExecutionStepInfos = fieldSubSelections.stream().map(executionResultNode -> {
                        return executionInfoFactory.newExecutionStepInfoForSubField(executionContext, sameFields, executionResultNode.getExecutionStepInfo());
                    }).collect(Collectors.toList());

                    CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalyzis = valueFetcher
                            .fetchBatchedValues(executionContext, sources, sameFields, newExecutionStepInfos)
                            .thenApply(fetchValue -> analyseValues(executionContext, fetchValue, name, sameFields, newExecutionStepInfos));
                    return fetchedValueAnalyzis;
                })
                .collect(toList());

        return Async.each(fetchedValues).thenApply(fetchedValuesMatrix -> {
            List<ExecutionResultZipper> result = new ArrayList<>();
            List<List<FetchedValueAnalysis>> newChildsPerNode = FpKit.transposeMatrix(fetchedValuesMatrix);

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
