package graphql.execution.nextgen;

import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MergedField;
import graphql.execution.MergedSelectionSet;
import graphql.execution.ResolveType;
import graphql.execution.nextgen.result.ExecutionResultNode;
import graphql.execution.nextgen.result.NamedResultNode;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.util.FpKit;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.FieldCollectorParameters.newParameters;

@Internal
public class ExecutionStrategyUtil {

    ExecutionStepInfoFactory executionStepInfoFactory = new ExecutionStepInfoFactory();
    FetchedValueAnalyzer fetchedValueAnalyzer = new FetchedValueAnalyzer();
    ValueFetcher valueFetcher = new ValueFetcher();
    ResultNodesCreator resultNodesCreator = new ResultNodesCreator();
    ResolveType resolveType = new ResolveType();
    FieldCollector fieldCollector = new FieldCollector();

    public List<CompletableFuture<NamedResultNode>> fetchSubSelection(ExecutionContext executionContext, FieldSubSelection fieldSubSelection) {
        List<CompletableFuture<FetchedValueAnalysis>> fetchedValueAnalysisList = fetchAndAnalyze(executionContext, fieldSubSelection);
        return fetchedValueAnalysisToNodesAsync(fetchedValueAnalysisList);
    }

    private List<CompletableFuture<FetchedValueAnalysis>> fetchAndAnalyze(ExecutionContext context, FieldSubSelection fieldSubSelection) {

        return FpKit.map(fieldSubSelection.getMergedSelectionSet().getSubFieldsList(),
                mergedField -> fetchAndAnalyzeField(context, fieldSubSelection.getSource(), mergedField, fieldSubSelection.getExecutionStepInfo()));

    }

    private CompletableFuture<FetchedValueAnalysis> fetchAndAnalyzeField(ExecutionContext context, Object source, MergedField mergedField,
                                                                         ExecutionStepInfo executionStepInfo) {

        ExecutionStepInfo newExecutionStepInfo = executionStepInfoFactory.newExecutionStepInfoForSubField(context, mergedField, executionStepInfo);
        return valueFetcher
                .fetchValue(context, source, mergedField, newExecutionStepInfo)
                .thenApply(fetchValue -> analyseValue(context, fetchValue, newExecutionStepInfo));
    }

    private List<CompletableFuture<NamedResultNode>> fetchedValueAnalysisToNodesAsync(List<CompletableFuture<FetchedValueAnalysis>> list) {
        return Async.map(list, fetchedValueAnalysis -> {
            ExecutionResultNode resultNode = resultNodesCreator.createResultNode(fetchedValueAnalysis);
            return new NamedResultNode(fetchedValueAnalysis.getField().getResultKey(), resultNode);
        });
    }

    public List<NamedResultNode> fetchedValueAnalysisToNodes(List<FetchedValueAnalysis> fetchedValueAnalysisList) {
        return FpKit.map(fetchedValueAnalysisList, fetchedValueAnalysis -> {
            ExecutionResultNode resultNode = resultNodesCreator.createResultNode(fetchedValueAnalysis);
            return new NamedResultNode(fetchedValueAnalysis.getField().getResultKey(), resultNode);
        });
    }


    private FetchedValueAnalysis analyseValue(ExecutionContext executionContext, FetchedValue fetchedValue, ExecutionStepInfo executionInfo) {
        FetchedValueAnalysis fetchedValueAnalysis = fetchedValueAnalyzer.analyzeFetchedValue(executionContext, fetchedValue, executionInfo);
        return fetchedValueAnalysis;
    }

    public FieldSubSelection createFieldSubSelection(ExecutionContext executionContext, FetchedValueAnalysis analysis) {
        ExecutionStepInfo executionInfo = analysis.getExecutionStepInfo();
        MergedField field = analysis.getField();
        Object source = analysis.getCompletedValue();

        GraphQLOutputType sourceType = executionInfo.getUnwrappedNonNullType();
        GraphQLObjectType resolvedObjectType = resolveType.resolveType(executionContext, field, source, executionInfo.getArguments(), sourceType);
        FieldCollectorParameters collectorParameters = newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(resolvedObjectType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();
        MergedSelectionSet subFields = fieldCollector.collectFields(collectorParameters,
                executionInfo.getField());

        // it is not really a new step but rather a refinement
        ExecutionStepInfo newExecutionStepInfoWithResolvedType = executionInfo.changeTypeWithPreservedNonNull(resolvedObjectType);

        FieldSubSelection fieldSubSelection = new FieldSubSelection();
        fieldSubSelection.setSource(source);
        fieldSubSelection.setExecutionStepInfo(newExecutionStepInfoWithResolvedType);
        fieldSubSelection.setMergedSelectionSet(subFields);
        return fieldSubSelection;
    }


}
