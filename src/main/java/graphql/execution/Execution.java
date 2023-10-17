package graphql.execution;


import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.engine.EngineParameters;
import graphql.engine.GraphQLEngine;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.extensions.ExtensionsBuilder;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.impl.SchemaUtil;
import graphql.util.LogKit;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;
import static graphql.execution.ExecutionStrategyParameters.newParameters;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.nonNullCtx;
import static java.util.concurrent.CompletableFuture.completedFuture;

@Internal
public class Execution {
    private static final Logger logNotSafe = LogKit.getNotPrivacySafeLogger(Execution.class);

    private final FieldCollector fieldCollector = new FieldCollector();
    private final GraphQLEngine graphQLEngine;
    private final ValueUnboxer valueUnboxer;

    public Execution(GraphQLEngine graphQLEngine, ValueUnboxer valueUnboxer) {
        this.graphQLEngine = graphQLEngine;
        this.valueUnboxer = valueUnboxer;
    }

    public CompletableFuture<ExecutionResult> execute(Document document, GraphQLSchema graphQLSchema, ExecutionId executionId, ExecutionInput executionInput, InstrumentationState instrumentationState) {

        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, executionInput.getOperationName());
        Map<String, FragmentDefinition> fragmentsByName = getOperationResult.fragmentsByName;
        OperationDefinition operationDefinition = getOperationResult.operationDefinition;

        RawVariables inputVariables = executionInput.getRawVariables();
        List<VariableDefinition> variableDefinitions = operationDefinition.getVariableDefinitions();

        CoercedVariables coercedVariables;
        try {
            coercedVariables = ValuesResolver.coerceVariableValues(graphQLSchema, variableDefinitions, inputVariables, executionInput.getGraphQLContext(), executionInput.getLocale());
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                return completedFuture(new ExecutionResultImpl((GraphQLError) rte));
            }
            throw rte;
        }

        Instrumentation instrumentation = graphQLEngine.getInstrumentation();

        ExecutionContext executionContext = newExecutionContextBuilder()
                .instrumentation(instrumentation)
                .instrumentationState(instrumentationState)
                .executionId(executionId)
                .graphQLSchema(graphQLSchema)
                .graphQLEngine(graphQLEngine)
                .context(executionInput.getContext())
                .graphQLContext(executionInput.getGraphQLContext())
                .localContext(executionInput.getLocalContext())
                .root(executionInput.getRoot())
                .fragmentsByName(fragmentsByName)
                .coercedVariables(coercedVariables)
                .document(document)
                .operationDefinition(operationDefinition)
                .dataLoaderRegistry(executionInput.getDataLoaderRegistry())
                .locale(executionInput.getLocale())
                .valueUnboxer(valueUnboxer)
                .executionInput(executionInput)
                .build();


        InstrumentationExecutionParameters parameters = new InstrumentationExecutionParameters(
                executionInput, graphQLSchema, instrumentationState
        );
        executionContext = instrumentation.instrumentExecutionContext(executionContext, parameters, instrumentationState);
        return executeOperation(executionContext, executionInput.getRoot(), executionContext.getOperationDefinition());
    }


    private CompletableFuture<ExecutionResult> executeOperation(ExecutionContext executionContext, Object root, OperationDefinition operationDefinition) {
        Instrumentation instrumentation = graphQLEngine.getInstrumentation();

        GraphQLContext graphQLContext = executionContext.getGraphQLContext();
        addExtensionsBuilderNotPresent(graphQLContext);

        InstrumentationExecuteOperationParameters instrumentationParams = new InstrumentationExecuteOperationParameters(executionContext);
        InstrumentationContext<ExecutionResult> executeOperationCtx = nonNullCtx(instrumentation.beginExecuteOperation(instrumentationParams, executionContext.getInstrumentationState()));

        OperationDefinition.Operation operation = operationDefinition.getOperation();
        GraphQLObjectType operationRootType;

        try {
            operationRootType = SchemaUtil.getOperationRootType(executionContext.getGraphQLSchema(), operationDefinition);
        } catch (RuntimeException rte) {
            if (rte instanceof GraphQLError) {
                ExecutionResult executionResult = new ExecutionResultImpl(Collections.singletonList((GraphQLError) rte));
                CompletableFuture<ExecutionResult> resultCompletableFuture = completedFuture(executionResult);

                executeOperationCtx.onDispatched(resultCompletableFuture);
                executeOperationCtx.onCompleted(executionResult, rte);
                return resultCompletableFuture;
            }
            throw rte;
        }

        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getCoercedVariables().toMap())
                .graphQLContext(graphQLContext)
                .build();

        MergedSelectionSet fields = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());

        ResultPath path = ResultPath.rootPath();
        ExecutionStepInfo executionStepInfo = newExecutionStepInfo().type(operationRootType).path(path).build();
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo);

        EngineParameters parameters = EngineParameters.newParameters()
                .operation(operation)
                .executionStepInfo(executionStepInfo)
                .source(root)
                .localContext(executionContext.getLocalContext())
                .fields(fields)
                .nonNullFieldValidator(nonNullableFieldValidator)
                .path(path)
                .build();

        CompletableFuture<ExecutionResult> result;
        try {
            result = graphQLEngine.execute(executionContext, parameters);
        } catch (NonNullableFieldWasNullException e) {
            // this means it was non-null types all the way from an offending non-null type
            // up to the root object type and there was a null value somewhere.
            //
            // The spec says we should return null for the data in this case
            //
            // https://spec.graphql.org/October2021/#sec-Handling-Field-Errors
            //
            result = completedFuture(new ExecutionResultImpl(null, executionContext.getErrors()));
        }

        // note this happens NOW - not when the result completes
        executeOperationCtx.onDispatched(result);

        // fill out extensions if we have them
        result = result.thenApply(er -> mergeExtensionsBuilderIfPresent(er, graphQLContext));

        result = result.whenComplete(executeOperationCtx::onCompleted);
        return result;
    }

    private void addExtensionsBuilderNotPresent(GraphQLContext graphQLContext) {
        Object builder = graphQLContext.get(ExtensionsBuilder.class);
        if (builder == null) {
            graphQLContext.put(ExtensionsBuilder.class, ExtensionsBuilder.newExtensionsBuilder());
        }
    }

    private ExecutionResult mergeExtensionsBuilderIfPresent(ExecutionResult executionResult, GraphQLContext graphQLContext) {
        Object builder = graphQLContext.get(ExtensionsBuilder.class);
        if (builder instanceof ExtensionsBuilder) {
            ExtensionsBuilder extensionsBuilder = (ExtensionsBuilder) builder;
            Map<Object, Object> currentExtensions = executionResult.getExtensions();
            if (currentExtensions != null) {
                extensionsBuilder.addValues(currentExtensions);
            }
            executionResult = extensionsBuilder.setExtensions(executionResult);
        }
        return executionResult;
    }
}
