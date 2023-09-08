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

        Object fieldValues = executePolymorphic(executionContext, parameters);
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        if (fieldValues instanceof CompletableFuture) {
            @SuppressWarnings("unchecked")
            CompletableFuture<Map<String, Object>> fieldResultsCF = (CompletableFuture<Map<String, Object>>) fieldValues;
            fieldResultsCF.whenComplete((fieldResults, exception) -> {
                if (exception != null) {
                    handleNonNullException(executionContext, overallResult, exception);
                    return;
                }
                ExecutionResultImpl executionResult = new ExecutionResultImpl(fieldResults, executionContext.getErrors());
                overallResult.complete(executionResult);
            });
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> fieldResults = (Map<String, Object>) fieldValues;
            ExecutionResultImpl executionResult = new ExecutionResultImpl(fieldResults, executionContext.getErrors());
            overallResult.complete(executionResult);
        }
        return overallResult;
    }

    @SuppressWarnings("unchecked")
    @Override
    public /* CompletableFuture<Map<String, Object>> | Map<String, Object> */ Object executePolymorphic(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

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
        CompletableFuture<Map<String, Object>> overallResult = new CompletableFuture<>();

        // TODO - we need new instrumentation call here saying we entered a ES recursively
        // TODO - something like beginObjectExecution()
        //executionStrategyCtx.onDispatched(overallResult);
        executionStrategyCtx.onDispatched(FAKE_ER_CF);

        Object awaitedFieldsWithInfo = futures.awaitPolymorphic();
        if (awaitedFieldsWithInfo instanceof CompletableFuture) {
            CompletableFuture<List<FieldValueInfo>> fieldsWithInfo = (CompletableFuture<List<FieldValueInfo>>) awaitedFieldsWithInfo;
            return handleAsyncFields(fieldsWithInfo, fieldNames, overallResult, executionStrategyCtx);
        } else {
            List<FieldValueInfo> fieldsWithInfo = (List<FieldValueInfo>) awaitedFieldsWithInfo;
            return handleSyncFields(fieldsWithInfo, fieldNames, overallResult, executionStrategyCtx);
        }
    }

    @NotNull
    private CompletableFuture<Map<String, Object>> handleAsyncFields(CompletableFuture<List<FieldValueInfo>> fieldWithInfo, List<String> fieldNames, CompletableFuture<Map<String, Object>> overallResult, ExecutionStrategyInstrumentationContext executionStrategyCtx) {
        fieldWithInfo.whenComplete((completeValueInfos, throwable) -> {
            BiConsumer<List<Object>, Throwable> handleResultsConsumer = buildAsyncFieldMap(fieldNames, overallResult);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }

            Async.CombinedBuilder<Object> executionResultFutures = Async.ofExpectedSize(completeValueInfos.size());
            for (FieldValueInfo completeValueInfo : completeValueInfos) {
                executionResultFutures.add(completeValueInfo.getFieldValueFuture());
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
        return overallResult;
    }

    @SuppressWarnings("unchecked")
    private /* CompletableFuture<Map<String, Object>> | Map<String, Object> */ Object handleSyncFields(List<FieldValueInfo> completeValueInfos, List<String> fieldNames, CompletableFuture<Map<String, Object>> overallResult, ExecutionStrategyInstrumentationContext executionStrategyCtx) {

        Async.CombinedBuilder<Object> executionResultFutures = Async.ofExpectedSize(completeValueInfos.size());
        for (FieldValueInfo completeValueInfo : completeValueInfos) {
            if (completeValueInfo.isFutureValue()) {
                executionResultFutures.add(completeValueInfo.getFieldValueFuture());
            } else {
                executionResultFutures.addObject(completeValueInfo.getFieldValueMaterialised());
            }
        }
        executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
        Object awaitedValues = executionResultFutures.awaitPolymorphic();
        if (awaitedValues instanceof CompletableFuture) {
            BiConsumer<List<Object>, Throwable> handleResultsConsumer = buildAsyncFieldMap(fieldNames, overallResult);

            CompletableFuture<List<Object>> completedValuesCF = (CompletableFuture<List<Object>>) awaitedValues;
            completedValuesCF.whenComplete(handleResultsConsumer);
            //
            // TODO - we need new instrumentation call here saying we entered a ES recursively
            // TODO - something like beginObjectExecution() so we can complete it here
            //overallResult.whenComplete(executionStrategyCtx::onCompleted);
            FAKE_ER_CF.whenComplete(executionStrategyCtx::onCompleted);
            return overallResult;
        } else {
            List<Object> completedValues = (List<Object>) awaitedValues;
            Map<String, Object> resultMap = buildFieldMap(fieldNames, completedValues);
            //
            // TODO - we need new instrumentation call here saying we entered a ES recursively
            // TODO - something like beginObjectExecution() so we can complete it here
            //executionStrategyCtx.onCompleted(resultMap, null);
            executionStrategyCtx.onCompleted(FAKE_ER, null);
            return resultMap;
        }
    }

    private Map<String, Object> buildFieldMap(List<String> fieldNames, List<Object> fieldResults) {
        Map<String, Object> resolvedValuesByField = Maps.newLinkedHashMapWithExpectedSize(fieldNames.size());
        int ix = 0;
        for (Object fieldResult : fieldResults) {
            String fieldName = fieldNames.get(ix++);
            resolvedValuesByField.put(fieldName, fieldResult);
        }
        return resolvedValuesByField;
    }

    private BiConsumer<List<Object>, Throwable> buildAsyncFieldMap(List<String> fieldNames, CompletableFuture<Map<String, Object>> overallResult) {
        return (List<Object> fieldResults, Throwable exception) -> {
            if (exception != null) {
                overallResult.completeExceptionally(exception);
                return;
            }
            Map<String, Object> resolvedValuesByField = Maps.newLinkedHashMapWithExpectedSize(fieldNames.size());
            int ix = 0;
            for (Object fieldResult : fieldResults) {
                String fieldName = fieldNames.get(ix++);
                resolvedValuesByField.put(fieldName, fieldResult);
            }
            overallResult.complete(resolvedValuesByField);
        };
    }

}
