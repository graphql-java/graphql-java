package graphql.execution;


import graphql.*;
import graphql.execution.incremental.IncrementalCallState;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.dataloader.FallbackDataLoaderDispatchStrategy;
import graphql.execution.instrumentation.dataloader.PerLevelDataLoaderDispatchStrategy;
import graphql.execution.instrumentation.dataloader.PerLevelDataLoaderDispatchStrategyWithDeferAlwaysDispatch;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.extensions.ExtensionsBuilder;
import graphql.incremental.DelayedIncrementalPartialResult;
import graphql.incremental.IncrementalExecutionResultImpl;
import graphql.language.*;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.impl.SchemaUtil;
import org.reactivestreams.Publisher;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static graphql.Directives.*;
import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;
import static graphql.execution.ExecutionStrategyParameters.newParameters;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.nonNullCtx;
import static graphql.execution.instrumentation.dataloader.EmptyDataLoaderRegistryInstance.EMPTY_DATALOADER_REGISTRY;
import static java.util.concurrent.CompletableFuture.completedFuture;

@Internal
public class Execution {
    private final FieldCollector fieldCollector = new FieldCollector();
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final Instrumentation instrumentation;
    private final ValueUnboxer valueUnboxer;
    private final boolean doNotAutomaticallyDispatchDataLoader;

    public Execution(ExecutionStrategy queryStrategy,
                     ExecutionStrategy mutationStrategy,
                     ExecutionStrategy subscriptionStrategy,
                     Instrumentation instrumentation,
                     ValueUnboxer valueUnboxer,
                     boolean doNotAutomaticallyDispatchDataLoader) {
        this.queryStrategy = queryStrategy != null ? queryStrategy : new AsyncExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new AsyncSerialExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new AsyncExecutionStrategy();
        this.instrumentation = instrumentation;
        this.valueUnboxer = valueUnboxer;
        this.doNotAutomaticallyDispatchDataLoader = doNotAutomaticallyDispatchDataLoader;
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

        ExecutionContext executionContext = newExecutionContextBuilder()
                .instrumentation(instrumentation)
                .instrumentationState(instrumentationState)
                .executionId(executionId)
                .graphQLSchema(graphQLSchema)
                .queryStrategy(queryStrategy)
                .mutationStrategy(mutationStrategy)
                .subscriptionStrategy(subscriptionStrategy)
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
                .propagateErrors(propagateErrors(coercedVariables, operationDefinition.getDirectives(), true))
                .build();

        executionContext.getGraphQLContext().put(ResultNodesInfo.RESULT_NODES_INFO, executionContext.getResultNodesInfo());

        InstrumentationExecutionParameters parameters = new InstrumentationExecutionParameters(
                executionInput, graphQLSchema
        );
        executionContext = instrumentation.instrumentExecutionContext(executionContext, parameters, instrumentationState);
        return executeOperation(executionContext, executionInput.getRoot(), executionContext.getOperationDefinition());
    }


    private CompletableFuture<ExecutionResult> executeOperation(ExecutionContext executionContext, Object root, OperationDefinition operationDefinition) {

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

                executeOperationCtx.onDispatched();
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

        MergedSelectionSet fields = fieldCollector.collectFields(
                collectorParameters,
                operationDefinition.getSelectionSet(),
                Optional.ofNullable(executionContext.getGraphQLContext())
                        .map(graphqlContext -> graphqlContext.getBoolean(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT))
                        .orElse(false)
        );

        ResultPath path = ResultPath.rootPath();
        ExecutionStepInfo executionStepInfo = newExecutionStepInfo().type(operationRootType).path(path).build();
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, executionStepInfo);

        ExecutionStrategyParameters parameters = newParameters()
                .executionStepInfo(executionStepInfo)
                .source(root)
                .localContext(executionContext.getLocalContext())
                .fields(fields)
                .nonNullFieldValidator(nonNullableFieldValidator)
                .path(path)
                .build();


        CompletableFuture<ExecutionResult> result;
        try {
            ExecutionStrategy executionStrategy = executionContext.getStrategy(operation);
            DataLoaderDispatchStrategy dataLoaderDispatchStrategy = createDataLoaderDispatchStrategy(executionContext, executionStrategy);
            executionContext.setDataLoaderDispatcherStrategy(dataLoaderDispatchStrategy);
            result = executionStrategy.execute(executionContext, parameters);
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
        executeOperationCtx.onDispatched();

        // fill out extensions if we have them
        result = result.thenApply(er -> mergeExtensionsBuilderIfPresent(er, graphQLContext));

        result = result.whenComplete(executeOperationCtx::onCompleted);

        return incrementalSupport(executionContext, result);
    }

    /*
     * Adds the deferred publisher if it's needed at the end of the query.  This is also a good time for the deferred code to start running
     */
    private CompletableFuture<ExecutionResult> incrementalSupport(ExecutionContext executionContext, CompletableFuture<ExecutionResult> result) {
        return result.thenApply(er -> {
            IncrementalCallState incrementalCallState = executionContext.getIncrementalCallState();
            if (incrementalCallState.getIncrementalCallsDetected()) {
                // we start the rest of the query now to maximize throughput.  We have the initial important results,
                // and now we can start the rest of the calls as early as possible (even before someone subscribes)
                Publisher<DelayedIncrementalPartialResult> publisher = incrementalCallState.startDeferredCalls();

                return IncrementalExecutionResultImpl.fromExecutionResult(er)
                        // "hasNext" can, in theory, be "false" when all the incremental items are delivered in the
                        // first response payload. However, the current implementation will never result in this.
                        // The behaviour might change if we decide to make optimizations in the future.
                        .hasNext(true)
                        .incrementalItemPublisher(publisher)
                        .build();
            }
            return er;
        });
    }

    private DataLoaderDispatchStrategy createDataLoaderDispatchStrategy(ExecutionContext executionContext, ExecutionStrategy executionStrategy) {
        if (executionContext.getDataLoaderRegistry() == EMPTY_DATALOADER_REGISTRY || doNotAutomaticallyDispatchDataLoader) {
            return DataLoaderDispatchStrategy.NO_OP;
        }
        if (! executionContext.isSubscriptionOperation()) {
            boolean deferEnabled = Optional.ofNullable(executionContext.getGraphQLContext())
                    .map(graphqlContext -> graphqlContext.getBoolean(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT))
                    .orElse(false);

            // Dedicated strategy for defer support, for safety purposes.
            return deferEnabled ?
                    new PerLevelDataLoaderDispatchStrategyWithDeferAlwaysDispatch(executionContext) :
                    new PerLevelDataLoaderDispatchStrategy(executionContext);
        } else {
            return new FallbackDataLoaderDispatchStrategy(executionContext);
        }
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

    private boolean propagateErrors(CoercedVariables variables, List<Directive> directives, boolean defaultValue) {
        Directive foundDirective = NodeUtil.findNodeByName(directives, ERROR_HANDLING_DIRECTIVE_DEFINITION.getName());
        if (foundDirective != null) {
            Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(ErrorHandlingDirective.getArguments(), foundDirective.getArguments(), variables, GraphQLContext.getDefault(), Locale.getDefault());
            Object flag = argumentValues.get("onError");
            Assert.assertTrue(flag instanceof String, "The '%s' directive MUST have a OnError value for the 'if' argument", ERROR_HANDLING_DIRECTIVE_DEFINITION.getName());
            return flag.equals(Enums.ON_ERROR_PROPAGATE);
        }
        return defaultValue;
    }
}
