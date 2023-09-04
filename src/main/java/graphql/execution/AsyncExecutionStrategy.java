package graphql.execution;

import com.google.common.collect.Maps;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
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
        return Async.asCompletableFuture(executePolymorphic(executionContext, parameters));
    }

    @SuppressWarnings("unchecked")
    @Override
    public /* CompletableFuture<ExecutionResult> | ExecutionResult */ Object executePolymorphic(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = ExecutionStrategyInstrumentationContext.nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters, executionContext.getInstrumentationState()));

        MergedSelectionSet fields = parameters.getFields();
        List<String> fieldNames = fields.getKeys();
        Async.CombinedBuilder<FieldValueInfo> futures = Async.ofExpectedSize(fields.size());
        for (String fieldName : fieldNames) {
            MergedField currentField = fields.getSubField(fieldName);

            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));

            Object fieldValueWithInfo = resolveFieldWithInfo(executionContext, newParameters);
            if (fieldValueWithInfo instanceof CompletableFuture) {
                CompletableFuture<FieldValueInfo> future = (CompletableFuture<FieldValueInfo>) fieldValueWithInfo;
                futures.add(future);
            } else {
                futures.addObject((FieldValueInfo) fieldValueWithInfo);
            }
        }
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        Object awaitedFieldsWithInfo = futures.awaitPolymorphic();
        if (awaitedFieldsWithInfo instanceof CompletableFuture) {
            CompletableFuture<List<FieldValueInfo>> fieldsWithInfo = (CompletableFuture<List<FieldValueInfo>>) awaitedFieldsWithInfo;
            return handleAsyncFields(executionContext, fieldsWithInfo, fieldNames, overallResult, executionStrategyCtx);
        } else {
            List<FieldValueInfo> fieldsWithInfo = (List<FieldValueInfo>) awaitedFieldsWithInfo;
            return handleSyncFields(executionContext, fieldsWithInfo, fieldNames, overallResult, executionStrategyCtx);
        }
    }

    @NotNull
    private CompletableFuture<ExecutionResult> handleAsyncFields(ExecutionContext executionContext, CompletableFuture<List<FieldValueInfo>> fieldWithInfo, List<String> fieldNames, CompletableFuture<ExecutionResult> overallResult, ExecutionStrategyInstrumentationContext executionStrategyCtx) {
        fieldWithInfo.whenComplete((completeValueInfos, throwable) -> {
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, fieldNames, overallResult);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }

            Async.CombinedBuilder<ExecutionResult> executionResultFutures = Async.ofExpectedSize(completeValueInfos.size());
            for (FieldValueInfo completeValueInfo : completeValueInfos) {
                executionResultFutures.add(completeValueInfo.getFieldValue());
            }
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
            executionResultFutures.await().whenComplete(handleResultsConsumer);
        }).exceptionally((ex) -> {
            // if there are any issues with combining/handling the field results,
            // complete the future at all costs and bubble up any thrown exception so
            // the execution does not hang.
            executionStrategyCtx.onFieldValuesException();
            overallResult.completeExceptionally(ex);
            return null;
        });
        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

    @SuppressWarnings("unchecked")
    private Object handleSyncFields(ExecutionContext executionContext, List<FieldValueInfo> completeValueInfos, List<String> fieldNames, CompletableFuture<ExecutionResult> overallResult, ExecutionStrategyInstrumentationContext executionStrategyCtx) {

        Async.CombinedBuilder<ExecutionResult> executionResultFutures = Async.ofExpectedSize(completeValueInfos.size());
        for (FieldValueInfo completeValueInfo : completeValueInfos) {
            if (completeValueInfo.isFutureValue()) {
                executionResultFutures.add(completeValueInfo.getFieldValue());
            } else {
                executionResultFutures.addObject(completeValueInfo.getFieldValueMaterialised());
            }
        }
        executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
        Object awaitedValues = executionResultFutures.awaitPolymorphic();
        if (awaitedValues instanceof CompletableFuture) {
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, fieldNames, overallResult);

            CompletableFuture<List<ExecutionResult>> completedValuesCF = (CompletableFuture<List<ExecutionResult>>) awaitedValues;
            completedValuesCF.whenComplete(handleResultsConsumer);

            overallResult.whenComplete(executionStrategyCtx::onCompleted);
            return overallResult;
        } else {
            List<ExecutionResult> completedValues = (List<ExecutionResult>) awaitedValues;
            ExecutionResult executionResult = handleSyncResults(executionContext, fieldNames, completedValues);
            executionStrategyCtx.onCompleted(executionResult, null);
            return executionResult;
        }
    }

    private ExecutionResult handleSyncResults(ExecutionContext executionContext, List<String> fieldNames, List<ExecutionResult> results) {
        Map<String, Object> resolvedValuesByField = Maps.newLinkedHashMapWithExpectedSize(fieldNames.size());
        int ix = 0;
        for (ExecutionResult executionResult : results) {
            String fieldName = fieldNames.get(ix++);
            resolvedValuesByField.put(fieldName, executionResult.getData());
        }
        return new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors());
    }
}
