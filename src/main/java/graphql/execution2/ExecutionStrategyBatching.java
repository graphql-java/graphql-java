package graphql.execution2;

import graphql.Assert;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution2.result.ExecutionResultMultiZipper;
import graphql.execution2.result.ExecutionResultNode;
import graphql.execution2.result.ExecutionResultZipper;
import graphql.execution2.result.ObjectExecutionResultNode;
import graphql.execution2.result.ObjectExecutionResultNode.RootExecutionResultNode;
import graphql.execution2.result.ObjectExecutionResultNode.UnresolvedObjectResultNode;
import graphql.execution2.result.ResultNodesUtil;
import graphql.language.Field;
import graphql.tuples.Tuple2;
import graphql.tuples.Tuples;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class ExecutionStrategyBatching {

    ExecutionStepInfoFactory executionInfoFactory;
    ValueFetcher valueFetcher;
    ResultNodesCreator resultNodesCreator = new ResultNodesCreator();

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;


    public ExecutionStrategyBatching(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.valueFetcher = new ValueFetcher(executionContext);
        this.executionInfoFactory = new ExecutionStepInfoFactory(executionContext);
    }

    public CompletableFuture<RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection) {
        CompletableFuture<RootExecutionResultNode> rootMono = fetchSubSelection(fieldSubSelection).thenApply(RootExecutionResultNode::new);

        return rootMono
                .thenCompose(rootNode -> {
                    ExecutionResultMultiZipper unresolvedNodes = ResultNodesUtil.getUnresolvedNodes(rootNode);
                    return nextStep(unresolvedNodes);
                })
                .thenApply(finalZipper -> finalZipper.toRootNode())
                .thenApply(RootExecutionResultNode.class::cast);
    }


    private CompletableFuture<Map<String, ExecutionResultNode>> fetchSubSelection(FieldSubSelection fieldSubSelection) {
        CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalysisFlux = fetchAndAnalyze(fieldSubSelection);
        return fetchedValueAnalysisFluxToNodes(fetchedValueAnalysisFlux);
    }

    private CompletableFuture<Map<String, ExecutionResultNode>> fetchedValueAnalysisFluxToNodes(CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalysisFlux) {
        CompletableFuture<List<Tuple2<String, ExecutionResultNode>>> tuplesList = Async.map(fetchedValueAnalysisFlux,
                fetchedValueAnalysis -> Tuples.of(fetchedValueAnalysis.getName(), resultNodesCreator.createResultNode(fetchedValueAnalysis)));
        return tuplesToMap(tuplesList);
    }


    private <U> CompletableFuture<Map<String, U>> tuplesToMap(CompletableFuture<List<Tuple2<String, U>>> tuplesFlux) {
        return Async.reduce(tuplesFlux, new LinkedHashMap<>(), (acc, tuple) -> {
            U value = tuple.getT2();
            acc.put(tuple.getT1(), value);
            return acc;
        });
    }

    private CompletableFuture<ExecutionResultMultiZipper> nextStep(ExecutionResultMultiZipper multizipper) {
        ExecutionResultMultiZipper nextUnresolvedNodes = ResultNodesUtil.getUnresolvedNodes(multizipper.toRootNode());
        if (nextUnresolvedNodes.getZippers().size() == 0) {
            return CompletableFuture.completedFuture(nextUnresolvedNodes);
        }
        List<ExecutionResultMultiZipper> groups = groupNodesIntoBatches(nextUnresolvedNodes);
        return nextStepImpl(groups).thenCompose(this::nextStep);
    }

    // all multizipper have the same root
    private CompletableFuture<ExecutionResultMultiZipper> nextStepImpl(List<ExecutionResultMultiZipper> unresolvedNodes) {
        Assert.assertNotEmpty(unresolvedNodes, "unresolvedNodes can't be empty");
        ExecutionResultNode commonRoot = unresolvedNodes.get(0).getCommonRoot();

        CompletableFuture<List<List<ExecutionResultZipper>>> listListCF = Async.flatMap(unresolvedNodes,
                executionResultMultiZipper -> fetchAndAnalyze(executionResultMultiZipper.getZippers()));

        return Common.flatList(listListCF)
                .thenApply(zippers -> new ExecutionResultMultiZipper(commonRoot, zippers));

    }

    private List<ExecutionResultMultiZipper> groupNodesIntoBatches(ExecutionResultMultiZipper unresolvedZipper) {
        Map<Map<String, List<Field>>, List<ExecutionResultZipper>> zipperBySubSelection = unresolvedZipper.getZippers().stream()
                .collect(groupingBy(executionResultZipper -> executionResultZipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection().getFields()));

        return zipperBySubSelection
                .entrySet()
                .stream()
                .map(entry -> new ExecutionResultMultiZipper(unresolvedZipper.getCommonRoot(), entry.getValue()))
                .collect(Collectors.toList());
    }

    //constrain: all fieldSubSelections have the same fields
    private CompletableFuture<List<ExecutionResultZipper>> fetchAndAnalyze(List<ExecutionResultZipper> unresolvedNodes) {
        Assert.assertTrue(unresolvedNodes.size() > 0, "unresolvedNodes can't be empty");

        List<FieldSubSelection> fieldSubSelections = unresolvedNodes.stream()
                .map(zipper -> zipper.getCurNode().getFetchedValueAnalysis().getFieldSubSelection())
                .collect(Collectors.toList());
        List<Object> sources = fieldSubSelections.stream().map(fieldSubSelection -> fieldSubSelection.getSource()).collect(Collectors.toList());

        // each field in the subSelection has n sources as input
        List<CompletableFuture<List<FetchedValueAnalysis>>> fetchedValues = fieldSubSelections
                .get(0)
                .getFields()
                .entrySet()
                .stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();

                    List<ExecutionStepInfo> newExecutionStepInfos = fieldSubSelections.stream().map(executionResultNode -> {
                        return executionInfoFactory.newExecutionStepInfoForSubField(sameFields, executionResultNode.getExecutionStepInfo());
                    }).collect(Collectors.toList());

                    CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalyzis = valueFetcher
                            .fetchBatchedValues(sources, sameFields, newExecutionStepInfos)
                            .thenApply(fetchValue -> analyseValues(fetchValue, name, sameFields, newExecutionStepInfos));
                    return fetchedValueAnalyzis;
                })
                .collect(toList());

        return Async.each(fetchedValues).thenApply(fetchedValuesMatrix -> {
            List<ExecutionResultZipper> result = new ArrayList<>();
            List<List<FetchedValueAnalysis>> newChildsPerNode = Common.transposeMatrix(fetchedValuesMatrix);

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
        UnresolvedObjectResultNode unresolvedNode = (UnresolvedObjectResultNode) unresolvedNodeZipper.getCurNode();
        Map<String, ExecutionResultNode> newChildren = fetchedValueAnalysisToNodes(fetchedValuesForNode);
        ObjectExecutionResultNode newNode = unresolvedNode.withChildren(newChildren);
        return unresolvedNodeZipper.withNode(newNode);
    }

    private Map<String, ExecutionResultNode> fetchedValueAnalysisToNodes(List<FetchedValueAnalysis> fetchedValueAnalysisFlux) {
        Map<String, ExecutionResultNode> result = new LinkedHashMap<>();
        fetchedValueAnalysisFlux.forEach(fetchedValueAnalysis -> {
            result.put(fetchedValueAnalysis.getName(), resultNodesCreator.createResultNode(fetchedValueAnalysis));
        });
        return result;
    }


    // only used for the root sub selection atm
    private CompletableFuture<List<FetchedValueAnalysis>> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
        List<CompletableFuture<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    ExecutionStepInfo newExecutionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(sameFields, fieldSubSelection.getExecutionStepInfo());
                    return valueFetcher
                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionStepInfo)
                            .thenApply(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionStepInfo));
                })
                .collect(toList());

        return Async.each(fetchedValues);
    }

    // only used for the root sub selection atm
    private FetchedValueAnalysis analyseValue(FetchedValue fetchedValue, String name, List<Field> field, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionInfo);
        fetchedValueAnalysis.setFetchedValue(fetchedValue);
        return fetchedValueAnalysis;
    }


    private List<FetchedValueAnalysis> analyseValues(List<FetchedValue> fetchedValues, String name, List<Field> field, List<ExecutionStepInfo> executionInfos) {
        List<FetchedValueAnalysis> result = new ArrayList<>();
        for (int i = 0; i < fetchedValues.size(); i++) {
            FetchedValue fetchedValue = fetchedValues.get(i);
            ExecutionStepInfo executionStepInfo = executionInfos.get(i);
            FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionStepInfo);
            fetchedValueAnalysis.setFetchedValue(fetchedValue);
            result.add(fetchedValueAnalysis);
        }
        return result;
    }

}
