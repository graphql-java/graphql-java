package graphql.execution.nextgen;

import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.nextgen.result.ExecutionResultMultiZipper;
import graphql.execution.nextgen.result.ExecutionResultNode;
import graphql.execution.nextgen.result.ExecutionResultZipper;
import graphql.execution.nextgen.result.NamedResultNode;
import graphql.execution.nextgen.result.ObjectExecutionResultNode;
import graphql.execution.nextgen.result.ResultNodesUtil;
import graphql.language.Field;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Internal
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
        this.executionInfoFactory = new ExecutionStepInfoFactory();
    }

    @Override
    public CompletableFuture<ObjectExecutionResultNode.RootExecutionResultNode> execute(FieldSubSelection fieldSubSelection) {
        return resolveSubSelection(fieldSubSelection)
                .thenApply(ObjectExecutionResultNode.RootExecutionResultNode::new);
    }

    private CompletableFuture<NamedResultNode> resolveNode(NamedResultNode namedResultNode) {
        ExecutionResultMultiZipper unresolvedMultiZipper = ResultNodesUtil.getUnresolvedNodes(namedResultNode.getNode());
        // must be a unresolved Node
        List<CompletableFuture<ExecutionResultZipper>> cfList = unresolvedMultiZipper
                .getZippers()
                .stream()
                .map(this::resolveZipper)
                .collect(Collectors.toList());
        return Async
                .each(cfList)
                .thenApply(unresolvedMultiZipper::withZippers)
                .thenApply(ExecutionResultMultiZipper::toRootNode)
                .thenApply(namedResultNode::withNode);
    }

    private CompletableFuture<ExecutionResultZipper> resolveZipper(ExecutionResultZipper unresolvedNodeZipper) {
        FetchedValueAnalysis fetchedValueAnalysis = unresolvedNodeZipper.getCurNode().getFetchedValueAnalysis();
        return resolveSubSelection(fetchedValueAnalysis.getFieldSubSelection())
                .thenApply(resolvedChildMap -> new ObjectExecutionResultNode(fetchedValueAnalysis, resolvedChildMap))
                .thenApply(unresolvedNodeZipper::withNode);
    }

    private CompletableFuture<List<NamedResultNode>> resolveSubSelection(FieldSubSelection fieldSubSelection) {
        return fetchSubSelection(fieldSubSelection)
                .thenCompose(children -> {
                    List<CompletableFuture<NamedResultNode>> listOfCF = children
                            .stream()
                            .map(this::resolveNode)
                            .collect(Collectors.toList());
                    return Async.each(listOfCF);
                });
    }


    private CompletableFuture<List<NamedResultNode>> fetchSubSelection(FieldSubSelection fieldSubSelection) {
        CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalysisFlux = fetchAndAnalyze(fieldSubSelection);
        return fetchedValueAnalysisToNodes(fetchedValueAnalysisFlux);
    }

    private CompletableFuture<List<NamedResultNode>> fetchedValueAnalysisToNodes(CompletableFuture<List<FetchedValueAnalysis>> fetchedValueAnalysisFlux) {
        return Async.map(fetchedValueAnalysisFlux,
                fetchedValueAnalysis -> {
                    ExecutionResultNode resultNode = resultNodesCreator.createResultNode(fetchedValueAnalysis);
                    return new NamedResultNode(fetchedValueAnalysis.getName(), resultNode);
                });
    }

    private CompletableFuture<List<FetchedValueAnalysis>> fetchAndAnalyze(FieldSubSelection fieldSubSelection) {
        List<CompletableFuture<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getFields().entrySet().stream()
                .map(entry -> {
                    List<Field> sameFields = entry.getValue();
                    String name = entry.getKey();
                    ExecutionStepInfo newExecutionStepInfo = executionInfoFactory.newExecutionStepInfoForSubField(executionContext, sameFields, fieldSubSelection.getExecutionStepInfo());
                    return valueFetcher
                            .fetchValue(fieldSubSelection.getSource(), sameFields, newExecutionStepInfo)
                            .thenApply(fetchValue -> analyseValue(fetchValue, name, sameFields, newExecutionStepInfo));
                })
                .collect(toList());
        return Async.each(fetchedValues);
    }

    private FetchedValueAnalysis analyseValue(FetchedValue fetchedValue, String name, List<Field> field, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(fetchedValue, name, field, executionInfo);
        return fetchedValueAnalysis;
    }

}
