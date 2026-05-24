package graphql.execution;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.execution.incremental.DeferredExecutionSupport;
import org.jspecify.annotations.NullMarked;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.introspection.Introspection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
@PublicApi
@NullMarked
public class AsyncExecutionStrategy extends AbstractAsyncExecutionStrategy {

    /**
     * The standard graphql execution strategy that runs fields asynchronously
     */
    public AsyncExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates a execution strategy that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use
     */
    public AsyncExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = executionContext.getDataLoaderDispatcherStrategy();
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = ExecutionStrategyInstrumentationContext.nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters, executionContext.getInstrumentationState()));

        MergedSelectionSet fields = parameters.getFields();
        List<String> fieldNames = fields.getKeys();

        Optional<ExecutionResult> isNotSensible = Introspection.isIntrospectionSensible(fields, executionContext);
        if (isNotSensible.isPresent()) {
            return CompletableFuture.completedFuture(isNotSensible.get());
        }

        DeferredExecutionSupport deferredExecutionSupport = createDeferredExecutionSupport(executionContext, parameters);

        dataLoaderDispatcherStrategy.executionStrategy(executionContext, parameters, deferredExecutionSupport.getNonDeferredFieldNames(fieldNames).size());
        Async.CombinedBuilder<FieldValueInfo> futures = getAsyncFieldValueInfo(executionContext, parameters, deferredExecutionSupport);
        dataLoaderDispatcherStrategy.finishedFetching(executionContext, parameters);


        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched();

        GraphQLContext graphQLContext = executionContext.getGraphQLContext();
        futures.await(graphQLContext).whenComplete((completeValueInfos, throwable) -> {
            List<String> fieldsExecutedOnInitialResult = deferredExecutionSupport.getNonDeferredFieldNames(fieldNames);

            throwable = executionContext.possibleCancellation(throwable);

            if (throwable != null) {
                if (capturePartialResults(executionContext) && completeValueInfos != null) {
                    // partial results: some FieldValueInfos completed before cancel - build with those
                    // null entries mean that FieldValueInfo CF wasn't done yet (field was cancelled)
                    Async.CombinedBuilder<Object> fieldValuesFutures = Async.ofExpectedSize(completeValueInfos.size());
                    for (FieldValueInfo completeValueInfo : completeValueInfos) {
                        if (completeValueInfo != null) {
                            fieldValuesFutures.addObject(completeValueInfo.getFieldValueObject());
                        } else {
                            fieldValuesFutures.addObject((Object) null);
                        }
                    }
                    List<FieldValueInfo> nonNullValueInfos = completeValueInfos.stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesInfo(nonNullValueInfos, parameters);
                    executionStrategyCtx.onFieldValuesInfo(nonNullValueInfos);
                    // Let handleResultsWithPartialData add the error to avoid duplication
                    BiConsumer<List<Object>, Throwable> fieldValuesConsumer = handleResultsWithPartialData(executionContext, fieldsExecutedOnInitialResult, overallResult);
                    fieldValuesFutures.await(graphQLContext).whenComplete(fieldValuesConsumer);
                    return;
                }
                Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                handleResults(executionContext, fieldsExecutedOnInitialResult, overallResult).accept(null, cause);
                return;
            }

            Async.CombinedBuilder<Object> fieldValuesFutures = Async.ofExpectedSize(completeValueInfos.size());
            for (FieldValueInfo completeValueInfo : completeValueInfos) {
                fieldValuesFutures.addObject(completeValueInfo.getFieldValueObject());
            }
            dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesInfo(completeValueInfos, parameters);
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);

            BiConsumer<List<Object>, Throwable> fieldValuesConsumer = handleResultsWithPartialData(executionContext, fieldsExecutedOnInitialResult, overallResult);
            fieldValuesFutures.await(graphQLContext).whenComplete(fieldValuesConsumer);
        }).exceptionally((ex) -> {
            // if there are any issues with combining/handling the field results,
            // complete the future at all costs and bubble up any thrown exception so
            // the execution does not hang.
            dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesException(ex, parameters);
            executionStrategyCtx.onFieldValuesException();
            overallResult.completeExceptionally(ex);
            return null;
        });

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

}
