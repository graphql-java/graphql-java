package graphql.execution;


import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.MutationNotSupportedError;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionContextParameters;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.NodeUtil;
import graphql.language.OperationDefinition;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.execution.ExecutionContextBuilder.newExecutionContextBuilder;
import static graphql.execution.ExecutionStrategyParameters.newParameters;
import static graphql.execution.ExecutionTypeInfo.newTypeInfo;
import static graphql.language.OperationDefinition.Operation.MUTATION;
import static graphql.language.OperationDefinition.Operation.QUERY;
import static graphql.language.OperationDefinition.Operation.SUBSCRIPTION;
import static java.util.concurrent.CompletableFuture.completedFuture;

@Internal
public class Execution {
    private static final Logger log = LoggerFactory.getLogger(Execution.class);

    private final FieldCollector fieldCollector = new FieldCollector();
    private final ExecutionStrategy queryStrategy;
    private final ExecutionStrategy mutationStrategy;
    private final ExecutionStrategy subscriptionStrategy;
    private final Instrumentation instrumentation;

    public Execution(ExecutionStrategy queryStrategy, ExecutionStrategy mutationStrategy, ExecutionStrategy subscriptionStrategy, Instrumentation instrumentation) {
        this.queryStrategy = queryStrategy != null ? queryStrategy : new AsyncExecutionStrategy();
        this.mutationStrategy = mutationStrategy != null ? mutationStrategy : new AsyncSerialExecutionStrategy();
        this.subscriptionStrategy = subscriptionStrategy != null ? subscriptionStrategy : new AsyncExecutionStrategy();
        this.instrumentation = instrumentation;
    }

    public CompletableFuture<ExecutionResult> execute(Document document, GraphQLSchema graphQLSchema, ExecutionId executionId, ExecutionInput executionInput, InstrumentationState instrumentationState) {

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
                .root(executionInput.getRoot())
                .fragmentsByName(fragmentsByName)
                .variables(coercedVariables)
                .document(document)
                .operationDefinition(operationDefinition)
                .build();


        InstrumentationExecutionContextParameters parameters = new InstrumentationExecutionContextParameters(
                executionInput, graphQLSchema, instrumentationState
        );
        executionContext = instrumentation.instrumentExecutionContext(executionContext, parameters);
        return executeOperation(executionContext, executionInput.getRoot(), executionContext.getOperationDefinition());
    }


    private CompletableFuture<ExecutionResult> executeOperation(ExecutionContext executionContext, Object root, OperationDefinition operationDefinition) {

        InstrumentationDataFetchParameters dataFetchParameters = new InstrumentationDataFetchParameters(executionContext);
        InstrumentationContext<CompletableFuture<ExecutionResult>> executionDispatchCtx = instrumentation.beginDataFetchDispatch(dataFetchParameters);
        InstrumentationContext<ExecutionResult> dataFetchCtx = instrumentation.beginDataFetch(dataFetchParameters);

        OperationDefinition.Operation operation = operationDefinition.getOperation();
        GraphQLObjectType operationRootType = getOperationRootType(executionContext.getGraphQLSchema(), operation);

        //
        // do we have a mutation operation root type.  The spec says if we don't then mutations are not allowed to be executed
        //
        // for the record earlier code has asserted that we have a query type in the schema since the spec says this is
        // ALWAYS required
        if (operation == MUTATION && operationRootType == null) {
            CompletableFuture<ExecutionResult> resultCompletableFuture = completedFuture(new ExecutionResultImpl(Collections.singletonList(new MutationNotSupportedError())));
            executionDispatchCtx.onEnd(resultCompletableFuture, null);
            return resultCompletableFuture;
        }

        FieldCollectorParameters collectorParameters = FieldCollectorParameters.newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(operationRootType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();

        Map<String, List<Field>> fields = fieldCollector.collectFields(collectorParameters, operationDefinition.getSelectionSet());

        ExecutionPath path = ExecutionPath.rootPath();
        ExecutionTypeInfo typeInfo = newTypeInfo().type(operationRootType).path(path).build();
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext, typeInfo);

        ExecutionStrategyParameters parameters = newParameters()
                .typeInfo(typeInfo)
                .source(root)
                .fields(fields)
                .nonNullFieldValidator(nonNullableFieldValidator)
                .path(path)
                .build();

        CompletableFuture<ExecutionResult> result;
        try {
            ExecutionStrategy executionStrategy;
            if (operation == OperationDefinition.Operation.MUTATION) {
                executionStrategy = mutationStrategy;
            } else if (operation == SUBSCRIPTION) {
                executionStrategy = subscriptionStrategy;
            } else {
                executionStrategy = queryStrategy;
            }
            log.debug("Executing '{}' query operation: '{}' using '{}' execution strategy", executionContext.getExecutionId(), operation, executionStrategy.getClass().getName());
            result = executionStrategy.execute(executionContext, parameters);
        } catch (NonNullableFieldWasNullException e) {
            // this means it was non null types all the way from an offending non null type
            // up to the root object type and there was a a null value some where.
            //
            // The spec says we should return null for the data in this case
            //
            // http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability
            //
            result = completedFuture(new ExecutionResultImpl(null, executionContext.getErrors()));
        }

        result = result.whenComplete(dataFetchCtx::onEnd);

        // note this happens NOW - not when the result completes
        executionDispatchCtx.onEnd(result, null);

        return result;
    }

    private GraphQLObjectType getOperationRootType(GraphQLSchema graphQLSchema, OperationDefinition.Operation operation) {
        if (operation == MUTATION) {
            return graphQLSchema.getMutationType();
        } else if (operation == QUERY) {
            return graphQLSchema.getQueryType();
        } else if (operation == SUBSCRIPTION) {
            return graphQLSchema.getSubscriptionType();
        } else {
            return assertShouldNeverHappen("Unhandled case.  An extra operation enum has been added without code support");
        }
    }
}
