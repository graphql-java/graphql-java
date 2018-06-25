package graphql.execution;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.execution.defer.DeferSupport;
import graphql.execution.defer.DeferredCall;
import graphql.execution.defer.DeferredErrorSupport;
import graphql.execution.instrumentation.DeferredFieldInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;


@PublicApi
public abstract class AbstractAsyncExecutionStrategy extends ExecutionStrategy {

    private final boolean executeSerial;

    public AbstractAsyncExecutionStrategy() {
        this.executeSerial = false;
    }

    public AbstractAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler) {
        super(dataFetcherExceptionHandler);
        this.executeSerial = false;
    }

    public AbstractAsyncExecutionStrategy(DataFetcherExceptionHandler dataFetcherExceptionHandler, boolean executeSerial) {
        super(dataFetcherExceptionHandler);
        this.executeSerial = executeSerial;
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        Map<String, List<Field>> fields = parameters.getFields();
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        List<CompletableFuture<FieldValueInfo>> futures = new ArrayList<>();
        List<String> resolvedFields = new ArrayList<>();

        CompletableFuture<List<FieldValueInfo>> resultFuture;
        if (executeSerial) {
            resultFuture = Async.eachSequentially(fieldNames, (fieldName, index, prevResults) -> {
                CompletableFuture<FieldValueInfo> future = resolveField(fieldName, fields, parameters, executionContext, executionStrategyCtx, resolvedFields);
                return future != null ? future : CompletableFuture.completedFuture(null);
            });
        } else {
            for (String fieldName : fieldNames) {
                CompletableFuture<FieldValueInfo> future = resolveField(fieldName, fields, parameters, executionContext, executionStrategyCtx, resolvedFields);
                if (future != null) {
                    futures.add(future);
                }
            }
            resultFuture = Async.each(futures);
        }


        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        resultFuture.whenComplete((completeValueInfos, throwable) -> {

            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, resolvedFields, overallResult);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }
            // filter out null FieldValueInfos from serial execution for deferred fields
            completeValueInfos = completeValueInfos.stream().filter(Objects::nonNull).collect(Collectors.toList());
            List<CompletableFuture<ExecutionResult>> executionResultFuture = completeValueInfos.stream().map(FieldValueInfo::getFieldValue).collect(Collectors.toList());
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
            Async.each(executionResultFuture).whenComplete(handleResultsConsumer);
        });

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

    private CompletableFuture<FieldValueInfo> resolveField(String fieldName, Map<String, List<Field>> fields,
                                                           ExecutionStrategyParameters parameters,
                                                           ExecutionContext executionContext,
                                                           ExecutionStrategyInstrumentationContext executionStrategyCtx,
                                                           List<String> resolvedFields) {

        List<Field> currentField = fields.get(fieldName);

        ExecutionPath fieldPath = parameters.getPath().segment(fieldName);
        ExecutionStrategyParameters newParameters = parameters
                .transform(builder -> builder.field(currentField).path(fieldPath).parent(parameters));

        if (isDeferred(executionContext, newParameters, currentField)) {
            executionStrategyCtx.onDeferredField(currentField);
            return null;
        }
        resolvedFields.add(fieldName);
        CompletableFuture<FieldValueInfo> future = resolveFieldWithInfo(executionContext, newParameters);
        return future;
    }

    private boolean isDeferred(ExecutionContext executionContext, ExecutionStrategyParameters parameters, List<Field> currentField) {
        DeferSupport deferSupport = executionContext.getDeferSupport();
        if (deferSupport.checkForDeferDirective(currentField)) {
            DeferredErrorSupport errorSupport = new DeferredErrorSupport();

            // with a deferred field we are really resetting where we execute from, that is from this current field onwards
            Map<String, List<Field>> fields = new HashMap<>();
            fields.put(currentField.get(0).getName(), currentField);

            ExecutionStrategyParameters callParameters = parameters.transform(builder ->
                    builder.deferredErrorSupport(errorSupport)
                            .field(currentField)
                            .fields(fields)
                            .parent(null) // this is a break in the parent -> child chain - its a new start effectively
                            .listSize(0)
                            .currentListIndex(0)
            );

            DeferredCall call = new DeferredCall(deferredExecutionResult(executionContext, callParameters), errorSupport);
            deferSupport.enqueue(call);
            return true;
        }
        return false;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private Supplier<CompletableFuture<ExecutionResult>> deferredExecutionResult(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        return () -> {
            GraphQLFieldDefinition fieldDef = getFieldDef(executionContext, parameters, parameters.getField().get(0));

            Instrumentation instrumentation = executionContext.getInstrumentation();
            DeferredFieldInstrumentationContext fieldCtx = instrumentation.beginDeferredField(
                    new InstrumentationDeferredFieldParameters(executionContext, parameters, fieldDef, fieldTypeInfo(parameters, fieldDef))
            );
            CompletableFuture<ExecutionResult> result = new CompletableFuture<>();
            fieldCtx.onDispatched(result);
            CompletableFuture<FieldValueInfo> fieldValueInfoFuture = resolveFieldWithInfo(executionContext, parameters);

            fieldValueInfoFuture.whenComplete((fieldValueInfo, throwable) -> {
                fieldCtx.onFieldValueInfo(fieldValueInfo);

                CompletableFuture<ExecutionResult> execResultFuture = fieldValueInfo.getFieldValue();
                execResultFuture = execResultFuture.whenComplete(fieldCtx::onCompleted);
                Async.copyResults(execResultFuture, result);
            });
            return result;
        };
    }

    protected BiConsumer<List<ExecutionResult>, Throwable> handleResults(ExecutionContext executionContext, List<String> fieldNames, CompletableFuture<ExecutionResult> overallResult) {
        return (List<ExecutionResult> results, Throwable exception) -> {
            if (exception != null) {
                handleNonNullException(executionContext, overallResult, exception);
                return;
            }
            Map<String, Object> resolvedValuesByField = new LinkedHashMap<>();
            int ix = 0;
            for (ExecutionResult executionResult : results) {

                String fieldName = fieldNames.get(ix++);
                resolvedValuesByField.put(fieldName, executionResult.getData());
            }
            overallResult.complete(new ExecutionResultImpl(resolvedValuesByField, executionContext.getErrors()));
        };
    }
}
