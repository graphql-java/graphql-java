package graphql.execution.nextgen;

import graphql.ExecutionInput;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MergedSelectionSet;
import graphql.execution.RawVariables;
import graphql.execution.ResultPath;
import graphql.execution.ValuesResolver;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Map;

import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;

/**
 * @deprecated Jan 2022 - We have decided to deprecate the NextGen engine, and it will be removed in a future release.
 */
@Deprecated
@Internal
public class ExecutionHelper {

    private final FieldCollector fieldCollector = new FieldCollector();

    public static class ExecutionData {
        public ExecutionContext executionContext;
    }

    public ExecutionData createExecutionData(Document document,
                                             GraphQLSchema graphQLSchema,
                                             ExecutionId executionId,
                                             ExecutionInput executionInput,
                                             InstrumentationState instrumentationState) {

        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, executionInput.getOperationName());
        Map<String, FragmentDefinition> fragmentsByName = getOperationResult.fragmentsByName;
        OperationDefinition operationDefinition = getOperationResult.operationDefinition;

        RawVariables inputVariables = executionInput.getRawVariables();
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();

        CoercedVariables coercedVariables = ValuesResolver.coerceVariableValues(graphQLSchema, variableDefinitions, inputVariables);

        ExecutionContext executionContext = newExecutionContextBuilder()
                .executionId(executionId)
                .instrumentationState(instrumentationState)
                .graphQLSchema(graphQLSchema)
                .context(executionInput.getContext())
                .graphQLContext(executionInput.getGraphQLContext())
                .root(executionInput.getRoot())
                .fragmentsByName(fragmentsByName)
                .coercedVariables(coercedVariables)
                .document(document)
                .operationDefinition(operationDefinition)
                .build();

        ExecutionData executionData = new ExecutionData();
        executionData.executionContext = executionContext;
        return executionData;
    }

    public FieldSubSelection getFieldSubSelection(ExecutionContext executionContext) {
        OperationDefinition operationDefinition = executionContext.getOperationDefinition();
        GraphQLObjectType operationRootType = Common.getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);

        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();

        MergedSelectionSet mergedSelectionSet = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());
        ExecutionStepInfo executionInfo = newExecutionStepInfo().type(operationRootType).path(ResultPath.rootPath()).build();

        FieldSubSelection fieldSubSelection = FieldSubSelection.newFieldSubSelection()
                .source(executionContext.getRoot())
                .localContext(executionContext.getLocalContext())
                .mergedSelectionSet(mergedSelectionSet)
                .executionInfo(executionInfo)
                .build();
        return fieldSubSelection;
    }
}
