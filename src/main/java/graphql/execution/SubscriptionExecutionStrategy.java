package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.execution.incremental.AlternativeCallContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.reactive.SubscriptionPublisher;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static graphql.Assert.assertTrue;
import static graphql.execution.instrumentation.SimpleInstrumentationContext.nonNullCtx;
import static java.util.Collections.singletonMap;

/**
 * An execution strategy that implements graphql subscriptions by using reactive-streams
 * as the output result of the subscription query.
 * <p>
 * Afterwards each object delivered on that stream will be mapped via running the original selection set over that object and hence producing an ExecutionResult
 * just like a normal graphql query.
 * <p>
 * See <a href="https://spec.graphql.org/draft/#sec-Subscription">https://spec.graphql.org/draft/#sec-Subscription</a>
 * <p>
 * See <a href="https://www.reactive-streams.org/">https://www.reactive-streams.org/</a>
 */
@PublicApi
public class SubscriptionExecutionStrategy extends ExecutionStrategy {

    /**
     * If a boolean value is placed into the {@link GraphQLContext} with this key then the order
     * of the subscription events can be controlled.   By default, subscription events are published
     * as the graphql subselection calls complete, and not in the order they originally arrived from the
     * source publisher.  But this can be changed to {@link Boolean#TRUE} to keep them in order.
     */
    public static final String KEEP_SUBSCRIPTION_EVENTS_ORDERED = "KEEP_SUBSCRIPTION_EVENTS_ORDERED";

    public SubscriptionExecutionStrategy() {
        super();
    }

    public SubscriptionExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);
        ExecutionStrategyInstrumentationContext executionStrategyCtx = ExecutionStrategyInstrumentationContext.nonNullCtx(instrumentation.beginExecutionStrategy(
                instrumentationParameters,
                executionContext.getInstrumentationState()
        ));

        CompletableFuture<Publisher<Object>> sourceEventStream = createSourceEventStream(executionContext, parameters);

        //
        // when the upstream source event stream completes, subscribe to it and wire in our adapter
        CompletableFuture<ExecutionResult> overallResult = sourceEventStream.thenApply((publisher) ->
        {
            if (publisher == null) {
                ExecutionResultImpl executionResult = new ExecutionResultImpl(null, executionContext.getErrors());
                return executionResult;
            }
            Function<Object, CompletionStage<ExecutionResult>> mapperFunction = eventPayload -> executeSubscriptionEvent(executionContext, parameters, eventPayload);
            boolean keepOrdered = keepOrdered(executionContext.getGraphQLContext());
            SubscriptionPublisher mapSourceToResponse = new SubscriptionPublisher(publisher, mapperFunction, keepOrdered);
            ExecutionResultImpl executionResult = new ExecutionResultImpl(mapSourceToResponse, executionContext.getErrors());
            return executionResult;
        });

        // dispatched the subscription query
        executionStrategyCtx.onDispatched();
        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

    private boolean keepOrdered(GraphQLContext graphQLContext) {
        return graphQLContext.getOrDefault(KEEP_SUBSCRIPTION_EVENTS_ORDERED, false);
    }


    /*
        https://github.com/facebook/graphql/blob/master/spec/Section%206%20--%20Execution.md

        CreateSourceEventStream(subscription, schema, variableValues, initialValue):

            Let {subscriptionType} be the root Subscription type in {schema}.
            Assert: {subscriptionType} is an Object type.
            Let {selectionSet} be the top level Selection Set in {subscription}.
            Let {rootField} be the first top level field in {selectionSet}.
            Let {argumentValues} be the result of {CoerceArgumentValues(subscriptionType, rootField, variableValues)}.
            Let {fieldStream} be the result of running {ResolveFieldEventStream(subscriptionType, initialValue, rootField, argumentValues)}.
            Return {fieldStream}.
     */

    private CompletableFuture<Publisher<Object>> createSourceEventStream(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        ExecutionStrategyParameters newParameters = firstFieldOfSubscriptionSelection(executionContext, parameters, false);

        CompletableFuture<FetchedValue> fieldFetched = Async.toCompletableFuture(fetchField(executionContext, newParameters));
        return fieldFetched.thenApply(fetchedValue -> {
            Object publisher = fetchedValue.getFetchedValue();
            if (publisher != null) {
                assertTrue(publisher instanceof Publisher, () -> "Your data fetcher must return a Publisher of events when using graphql subscriptions");
            }
            //noinspection unchecked,DataFlowIssue
            return (Publisher<Object>) publisher;
        });
    }

    /*
        ExecuteSubscriptionEvent(subscription, schema, variableValues, initialValue):

        Let {subscriptionType} be the root Subscription type in {schema}.
        Assert: {subscriptionType} is an Object type.
        Let {selectionSet} be the top level Selection Set in {subscription}.
        Let {data} be the result of running {ExecuteSelectionSet(selectionSet, subscriptionType, initialValue, variableValues)} normally (allowing parallelization).
        Let {errors} be any field errors produced while executing the selection set.
        Return an unordered map containing {data} and {errors}.

        Note: The {ExecuteSubscriptionEvent()} algorithm is intentionally similar to {ExecuteQuery()} since this is how each event result is produced.
     */

    private CompletableFuture<ExecutionResult> executeSubscriptionEvent(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Object eventPayload) {

        Instrumentation instrumentation = executionContext.getInstrumentation();

        ExecutionContext newExecutionContext = executionContext.transform(builder -> builder
                .root(eventPayload)
                .resetErrors()
        );
        ExecutionStrategyParameters newParameters = firstFieldOfSubscriptionSelection(newExecutionContext, parameters, true);
        ExecutionStepInfo subscribedFieldStepInfo = createSubscribedFieldStepInfo(executionContext, newParameters);

        InstrumentationFieldParameters i13nFieldParameters = new InstrumentationFieldParameters(executionContext, () -> subscribedFieldStepInfo);
        InstrumentationContext<ExecutionResult> subscribedFieldCtx = nonNullCtx(instrumentation.beginSubscribedFieldEvent(
                i13nFieldParameters, executionContext.getInstrumentationState()
        ));

        FetchedValue fetchedValue = unboxPossibleDataFetcherResult(newExecutionContext, newParameters, eventPayload);
        FieldValueInfo fieldValueInfo = completeField(newExecutionContext, newParameters, fetchedValue);
        executionContext.getDataLoaderDispatcherStrategy().newSubscriptionExecution(fieldValueInfo, newParameters.getDeferredCallContext());
        CompletableFuture<ExecutionResult> overallResult = fieldValueInfo
                .getFieldValueFuture()
                .thenApply(val -> new ExecutionResultImpl(val, newParameters.getDeferredCallContext().getErrors()))
                .thenApply(executionResult -> wrapWithRootFieldName(newParameters, executionResult));

        // dispatch instrumentation so they can know about each subscription event
        subscribedFieldCtx.onDispatched();
        overallResult.whenComplete(subscribedFieldCtx::onCompleted);

        // allow them to instrument each ER should they want to
        InstrumentationExecutionParameters i13nExecutionParameters = new InstrumentationExecutionParameters(
                executionContext.getExecutionInput(), executionContext.getGraphQLSchema());

        overallResult = overallResult.thenCompose(executionResult -> instrumentation.instrumentExecutionResult(executionResult, i13nExecutionParameters, executionContext.getInstrumentationState()));
        return overallResult;
    }

    private ExecutionResult wrapWithRootFieldName(ExecutionStrategyParameters parameters, ExecutionResult executionResult) {
        String rootFieldName = getRootFieldName(parameters);
        return new ExecutionResultImpl(
                singletonMap(rootFieldName, executionResult.getData()),
                executionResult.getErrors()
        );
    }

    private String getRootFieldName(ExecutionStrategyParameters parameters) {
        Field rootField = parameters.getField().getSingleField();
        return rootField.getResultKey();
    }

    private ExecutionStrategyParameters firstFieldOfSubscriptionSelection(ExecutionContext executionContext,
                                                                          ExecutionStrategyParameters parameters,
                                                                          boolean newCallContext) {
        MergedSelectionSet fields = parameters.getFields();
        MergedField firstField = fields.getSubField(fields.getKeys().get(0));

        ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(firstField.getSingleField()));
        NonNullableFieldValidator nonNullableFieldValidator = new NonNullableFieldValidator(executionContext);


        return parameters.transform(builder -> {
            builder
                    .field(firstField)
                    .path(fieldPath)
                    .nonNullFieldValidator(nonNullableFieldValidator);
            if (newCallContext) {
                builder.deferredCallContext(new AlternativeCallContext(1, 1));
            }
        });

    }

    private ExecutionStepInfo createSubscribedFieldStepInfo(ExecutionContext
                                                                    executionContext, ExecutionStrategyParameters parameters) {
        Field field = parameters.getField().getSingleField();
        GraphQLObjectType parentType = (GraphQLObjectType) parameters.getExecutionStepInfo().getUnwrappedNonNullType();
        GraphQLFieldDefinition fieldDef = getFieldDef(executionContext.getGraphQLSchema(), parentType, field);
        return createExecutionStepInfo(executionContext, parameters, fieldDef, parentType);
    }
}
