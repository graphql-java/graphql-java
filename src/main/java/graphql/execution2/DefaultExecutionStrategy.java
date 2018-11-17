package graphql.execution2;

import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution2.result.ExecutionResultMultiZipper;
import graphql.execution2.result.ExecutionResultNode;
import graphql.execution2.result.ExecutionResultZipper;
import graphql.execution2.result.ObjectExecutionResultNode;
import graphql.execution2.result.ObjectExecutionResultNode.UnresolvedObjectResultNode;
import graphql.execution2.result.ResultNodesUtil;
import graphql.language.Field;
import graphql.tuples.Tuple2;
import graphql.tuples.Tuples;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class DefaultExecutionStrategy implements ExecutionStrategy {

    ExecutionStepInfoFactory executionInfoFactory;
    ValueFetcher valueFetcher;
    ResultNodesCreator resultNodesCreator = new ResultNodesCreator();

    private final ExecutionContext executionContext;
    private FetchedValueAnalyzer fetchedValueAnalyzer;

    public DefaultExecutionStrategy(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.fetchedValueAnalyzer = new FetchedValueAnalyzer(executionContext);
        this.valueFetcher = new ValueFetcher(executionContext);
        this.executionInfoFactory = new ExecutionStepInfoFactory(executionContext);
    }

    @Override
    public CompletableFuture<ObjectExecutionResultNode.RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection) {
        return fetchSubSelection(fieldSubSelection).thenCompose(childMap -> {
            List<CompletableFuture<Tuple2<String, ExecutionResultNode>>> listOfCF = childMap
                    .entrySet()
                    .stream()
                    .map(entry -> resolveNode(entry.getValue()).thenApply(resolvedNode -> Tuples.of(entry.getKey(), resolvedNode)))
                    .collect(Collectors.toList());
            return tuplesToMap(Async.each(listOfCF))
                    .thenApply(ObjectExecutionResultNode.RootExecutionResultNode::new);
        });
    }

    private CompletableFuture<ExecutionResultNode> resolveNode(ExecutionResultNode rootNode) {
        ExecutionResultMultiZipper unresolvedMultiZipper = ResultNodesUtil.getUnresolvedNodes(rootNode);
        // must be a unresolved Node
        List<CompletableFuture<ExecutionResultZipper>> cfList = unresolvedMultiZipper
                .getZippers()
                .stream()
                .map(unresolvedNodeZipper -> {
                    ExecutionResultNode node = unresolvedNodeZipper.getCurNode();
                    return resolveSubSelection((UnresolvedObjectResultNode) node)
                            .thenApply(unresolvedNodeZipper::withNode);
                })
                .collect(Collectors.toList());

        return Async
                .each(cfList)
                .thenApply(unresolvedMultiZipper::withZippers)
                .thenApply(ExecutionResultMultiZipper::toRootNode);
    }

    private CompletableFuture<ExecutionResultNode> resolveSubSelection(UnresolvedObjectResultNode unresolvedNode) {
        return fetchSubSelection(unresolvedNode.getFetchedValueAnalysis().getFieldSubSelection()).thenCompose(childMap -> {
            List<CompletableFuture<Tuple2<String, ExecutionResultNode>>> listOfCF = childMap
                    .entrySet()
                    .stream()
                    .map(entry -> resolveNode(entry.getValue()).thenApply(resolvedNode -> Tuples.of(entry.getKey(), resolvedNode)))
                    .collect(Collectors.toList());
            return tuplesToMap(Async.each(listOfCF))
                    .thenApply(resolvedChildMap -> new ObjectExecutionResultNode(unresolvedNode.getFetchedValueAnalysis(), resolvedChildMap));
        });
    }

    private CompletableFuture<Map<String, ExecutionResultNode>> fetchSubSelection(FieldSubSelection fieldSubSelection) {
        CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalysisFlux = fetchAndAnalyze(fieldSubSelection);
        return fetchedValueAnalysisToNodes(fetchedValueAnalysisFlux);
    }

    private CompletableFuture<Map<String, ExecutionResultNode>> fetchedValueAnalysisToNodes(CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalysisFlux) {
        CompletableFuture<List<Tuple2<String, ExecutionResultNode>>> tuplesList = Async.map(fetchedValueAnalysisFlux,
                fetchedValueAnalysis -> {
                    ExecutionResultNode resultNode = resultNodesCreator.createResultNode(fetchedValueAnalysis);
                    return Tuples.of(fetchedValueAnalysis.getName(), resultNode);
                });
        return tuplesToMap(tuplesList);
    }


    private <U> CompletableFuture<Map<String, U>> tuplesToMap(CompletableFuture<List<Tuple2<String, U>>> tuplesFlux) {
        return Async.reduce(tuplesFlux, new LinkedHashMap<>(), (acc, tuple) -> {
            U value = tuple.getT2();
            acc.put(tuple.getT1(), value);
            return acc;
        });
    }


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

    private FetchedValueAnalysis analyseValue(FetchedValue fetchedValue, String name, List<Field> field, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue.getFetchedValue(), name, field, executionInfo);
        fetchedValueAnalysis.setFetchedValue(fetchedValue);
        return fetchedValueAnalysis;
    }

}
