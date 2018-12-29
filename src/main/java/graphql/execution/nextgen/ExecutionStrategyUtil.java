package graphql.execution.nextgen;

import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.MergedField;
import graphql.execution.nextgen.result.ExecutionResultNode;
import graphql.execution.nextgen.result.NamedResultNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;

public class ExecutionStrategyUtil {
    ExecutionStepInfoFactory executionStepInfoFactory = new ExecutionStepInfoFactory();
    FetchedValueAnalyzer fetchedValueAnalyzer = new FetchedValueAnalyzer();
    ValueFetcher valueFetcher = new ValueFetcher();
    ResultNodesCreator resultNodesCreator = new ResultNodesCreator();

    public List<CompletableFuture<NamedResultNode>> fetchSubSelection(ExecutionContext executionContext, FieldSubSelection fieldSubSelection) {
        List<CompletableFuture<FetchedValueAnalysis>> fetchedValueAnalysisList = fetchAndAnalyze(executionContext, fieldSubSelection);
        return Async.map(fetchedValueAnalysisList, fetchedValueAnalysis -> {
            ExecutionResultNode resultNode = resultNodesCreator.createResultNode(fetchedValueAnalysis);
            return new NamedResultNode(fetchedValueAnalysis.getName(), resultNode);
        });
    }

    private List<CompletableFuture<FetchedValueAnalysis>> fetchAndAnalyze(ExecutionContext context, FieldSubSelection fieldSubSelection) {

        List<CompletableFuture<FetchedValueAnalysis>> fetchedValues = fieldSubSelection.getSubFields().entrySet().stream()
                .map(entry -> mapMergedField(context, fieldSubSelection.getSource(), entry.getKey(), entry.getValue(), fieldSubSelection.getExecutionStepInfo()))
                .collect(toList());
        return fetchedValues;
    }

    private CompletableFuture<FetchedValueAnalysis> mapMergedField(ExecutionContext context, Object source, String key, MergedField mergedField,
                                                                   ExecutionStepInfo executionStepInfo) {

        ExecutionStepInfo newExecutionStepInfo = executionStepInfoFactory.newExecutionStepInfoForSubField(context, mergedField, executionStepInfo);
        return valueFetcher
                .fetchValue(context, source, mergedField, newExecutionStepInfo)
                .thenApply(fetchValue -> analyseValue(context, fetchValue, key, mergedField, newExecutionStepInfo));
    }

    private FetchedValueAnalysis analyseValue(ExecutionContext executionContext, FetchedValue fetchedValue, String name, MergedField field, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(executionContext, fetchedValue, name, field, executionInfo);
        return fetchedValueAnalysis;
    }


}
