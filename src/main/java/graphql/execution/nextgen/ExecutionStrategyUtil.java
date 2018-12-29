package graphql.execution.nextgen;

import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.MergedField;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;

public class ExecutionStrategyUtil {
    ExecutionStepInfoFactory executionStepInfoFactory = new ExecutionStepInfoFactory();
    FetchedValueAnalyzer fetchedValueAnalyzer = new FetchedValueAnalyzer();
    ValueFetcher valueFetcher = new ValueFetcher();

    public List<CompletableFuture<FetchedValueAnalysis>> fetchAndAnalyze(ExecutionContext context, FieldSubSelection fieldSubSelection) {

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
