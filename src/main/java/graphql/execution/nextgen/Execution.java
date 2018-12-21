package graphql.execution.nextgen;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MergedSelectionSet;
import graphql.execution.ValuesResolver;
import graphql.execution.nextgen.result.ResultNodesUtil;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;

@Internal
public class Execution {

    private final FieldCollector fieldCollector = new FieldCollector();

    public CompletableFuture<ExecutionResult> execute(Class<? extends ExecutionStrategy> executionStrategy, Document document,
                                                      GraphQLSchema graphQLSchema,
                                                      ExecutionId executionId,
                                                      ExecutionInput executionInput) {
        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, executionInput.getOperationName());
        Map<String, FragmentDefinition> fragmentsByName = getOperationResult.fragmentsByName;
        OperationDefinition operationDefinition = getOperationResult.operationDefinition;

        ValuesResolver valuesResolver = new ValuesResolver();
        Map<String, Object> inputVariables = executionInput.getVariables();
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();

        Map<String, Object> coercedVariables;
        try {
            coercedVariables = valuesResolver.coerceArgumentValues(graphQLSchema, variableDefinitions, inputVariables);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                return CompletableFuture.completedFuture(new ExecutionResultImpl((GraphQLError) rte));
            }

            return Async.exceptionallyCompletedFuture(rte);
        }

        ExecutionContext executionContext = newExecutionContextBuilder()
                .executionId(executionId)
                .graphQLSchema(graphQLSchema)
                .context(executionInput.getContext())
                .root(executionInput.getRoot())
                .fragmentsByName(fragmentsByName)
                .variables(coercedVariables)
                .document(document)
                .operationDefinition(operationDefinition)
                .dataLoaderRegistry(executionInput.getDataLoaderRegistry())
                .build();

        return executeOperation(executionStrategy, executionContext, executionInput.getRoot(), executionContext.getOperationDefinition());
    }


    private CompletableFuture<ExecutionResult> executeOperation(Class<? extends ExecutionStrategy> executionStrategy, ExecutionContext executionContext, Object root, OperationDefinition operationDefinition) {

        GraphQLObjectType operationRootType;

        operationRootType = Common.getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);

        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();
        MergedSelectionSet mergedSelectionSet = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());
        ExecutionStepInfo executionInfo = newExecutionStepInfo().type(operationRootType).path(ExecutionPath.rootPath()).build();

        FieldSubSelection fieldSubSelection = new FieldSubSelection();
        fieldSubSelection.setSource(root);
        fieldSubSelection.setMergedSelectionSet(mergedSelectionSet);
        fieldSubSelection.setExecutionStepInfo(executionInfo);

        try {
            return executionStrategy
                    .getConstructor(ExecutionContext.class)
                    .newInstance(executionContext)
                    .execute(fieldSubSelection)
                    .thenApply(ResultNodesUtil::toExecutionResult);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
