package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.execution.incremental.DeferredExecutionSupport;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.introspection.Introspection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
@PublicApi
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
        boolean noOpInstr = executionContext.isNoOpFieldInstrumentation();

        ExecutionStrategyInstrumentationContext executionStrategyCtx;
        if (noOpInstr) {
            executionStrategyCtx = ExecutionStrategyInstrumentationContext.NOOP;
        } else {
            Instrumentation instrumentation = executionContext.getInstrumentation();
            InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);
            executionStrategyCtx = ExecutionStrategyInstrumentationContext.nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters, executionContext.getInstrumentationState()));
        }

        MergedSelectionSet fields = parameters.getFields();
        List<String> fieldNames = fields.getKeys();

        Optional<ExecutionResult> isNotSensible = Introspection.isIntrospectionSensible(fields, executionContext);
        if (isNotSensible.isPresent()) {
            return CompletableFuture.completedFuture(isNotSensible.get());
        }

        DeferredExecutionSupport deferredExecutionSupport = createDeferredExecutionSupport(executionContext, parameters);

        dataLoaderDispatcherStrategy.executionStrategy(executionContext, parameters, deferredExecutionSupport.getNonDeferredFieldNames(fieldNames).size());
        Object resolvedFieldResult = getAsyncFieldValueInfo(executionContext, parameters, deferredExecutionSupport);
        dataLoaderDispatcherStrategy.finishedFetching(executionContext, parameters);


        executionStrategyCtx.onDispatched();

        // getAsyncFieldValueInfo returns either a List<FieldValueInfo> (materialized) or a CombinedBuilder
        Object fieldValueInfosResult;
        if (resolvedFieldResult instanceof List) {
            fieldValueInfosResult = resolvedFieldResult;
        } else {
            @SuppressWarnings("unchecked")
            Async.CombinedBuilder<FieldValueInfo> builder = (Async.CombinedBuilder<FieldValueInfo>) resolvedFieldResult;
            fieldValueInfosResult = builder.awaitPolymorphic();
        }
        if (fieldValueInfosResult instanceof CompletableFuture) {
            @SuppressWarnings("unchecked")
            CompletableFuture<List<FieldValueInfo>> fieldValueInfosCF = (CompletableFuture<List<FieldValueInfo>>) fieldValueInfosResult;
            CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
            fieldValueInfosCF.whenComplete((completeValueInfos, throwable) -> {
                List<String> fieldsExecutedOnInitialResult = deferredExecutionSupport.getNonDeferredFieldNames(fieldNames);

                BiConsumer<List<Object>, Throwable> handleResultsConsumer = handleResults(executionContext, fieldsExecutedOnInitialResult, overallResult);
                throwable = executionContext.possibleCancellation(throwable);

                if (throwable != null) {
                    handleResultsConsumer.accept(null, throwable.getCause());
                    return;
                }

                Async.CombinedBuilder<Object> fieldValuesFutures = Async.ofExpectedSize(completeValueInfos.size());
                for (FieldValueInfo completeValueInfo : completeValueInfos) {
                    fieldValuesFutures.addObject(completeValueInfo.getFieldValueObject());
                }
                dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesInfo(completeValueInfos, parameters);
                executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
                fieldValuesFutures.await().whenComplete(handleResultsConsumer);
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
        } else {
            @SuppressWarnings("unchecked")
            List<FieldValueInfo> completeValueInfos = (List<FieldValueInfo>) fieldValueInfosResult;
            List<String> fieldsExecutedOnInitialResult = deferredExecutionSupport.getNonDeferredFieldNames(fieldNames);

            Async.CombinedBuilder<Object> fieldValuesFutures = Async.ofExpectedSize(completeValueInfos.size());
            for (FieldValueInfo completeValueInfo : completeValueInfos) {
                fieldValuesFutures.addObject(completeValueInfo.getFieldValueObject());
            }
            dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesInfo(completeValueInfos, parameters);
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);

            Object completedValuesObject = fieldValuesFutures.awaitPolymorphic();
            if (completedValuesObject instanceof CompletableFuture) {
                @SuppressWarnings("unchecked")
                CompletableFuture<List<Object>> completedValues = (CompletableFuture<List<Object>>) completedValuesObject;
                CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
                BiConsumer<List<Object>, Throwable> handleResultsConsumer = handleResults(executionContext, fieldsExecutedOnInitialResult, overallResult);
                completedValues.whenComplete(handleResultsConsumer);
                overallResult.whenComplete(executionStrategyCtx::onCompleted);
                return overallResult;
            } else {
                @SuppressWarnings("unchecked")
                List<Object> results = (List<Object>) completedValuesObject;
                Map<String, Object> resolvedValuesByField = executionContext.getResponseMapFactory().createInsertionOrdered(fieldsExecutedOnInitialResult, results);
                ExecutionResult executionResult = new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors());
                executionStrategyCtx.onCompleted(executionResult, null);
                return CompletableFuture.completedFuture(executionResult);
            }
        }
    }

}
