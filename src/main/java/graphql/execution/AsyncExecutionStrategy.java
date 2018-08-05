package graphql.execution;

import graphql.ExecutionResult;
import graphql.execution.defer.DeferSupport;
import graphql.execution.defer.DeferredCall;
import graphql.execution.defer.DeferredErrorSupport;
import graphql.execution.instrumentation.DeferredFieldInstrumentationContext;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationDeferredFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.lazy.LazyExecutionResult;
import graphql.language.Field;
import graphql.schema.GraphQLFieldDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
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

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = instrumentation.beginExecutionStrategy(instrumentationParameters);

        CompletionCancellationRegistry completionCancellationRegistry = new CompletionCancellationRegistry(
                parameters.getCompletionCancellationRegistry()
        );

        Map<String, List<Field>> fields = parameters.getFields();
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        List<CompletableFuture<FieldValueInfo>> futures = new ArrayList<>();
        List<String> resolvedFields = new ArrayList<>();
        for (String fieldName : fieldNames) {
            List<Field> currentField = fields.get(fieldName);

            ExecutionPath fieldPath = parameters.getPath().segment(fieldName);
            ExecutionStrategyParameters newParameters = parameters.transform(builder ->
                    builder.field(currentField)
                            .path(fieldPath)
                            .parent(parameters)
                            .completionCancellationRegistry(completionCancellationRegistry)
            );

            if (isDeferred(executionContext, newParameters, currentField)) {
                executionStrategyCtx.onDeferredField(currentField);
                continue;
            }
            resolvedFields.add(fieldName);
            CompletableFuture<FieldValueInfo> future = resolveFieldWithInfo(executionContext, newParameters);
            futures.add(future);
        }
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        Async.each(futures).whenComplete((completeValueInfos, throwable) -> {
            BiConsumer<List<ExecutionResult>, Throwable> handleResultsConsumer = handleResults(executionContext, completionCancellationRegistry, resolvedFields, overallResult);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }
            List<CompletableFuture<ExecutionResult>> executionResultFuture = completeValueInfos.stream().map(FieldValueInfo::getFieldValue).collect(Collectors.toList());
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
            Async.each(executionResultFuture).whenComplete(handleResultsConsumer);
        });

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }

    private boolean isDeferred(ExecutionContext executionContext, ExecutionStrategyParameters parameters, List<Field> currentField) {
        DeferSupport deferSupport = executionContext.getDeferSupport();
        if (deferSupport.checkForDeferDirective(currentField)) {
            DeferredErrorSupport errorSupport = new DeferredErrorSupport();

            // with a deferred field we are really resetting where we execute from, that is from this current field onwards
            Map<String, List<Field>> fields = new HashMap<>();
            fields.put(currentField.get(0).getName(), currentField);

            // Even if the parent is cancelled, this execution is still enqueued in the defer support
            // and can thus be completed independently. Therefore we create a new top-level registry
            // here.
            CompletionCancellationRegistry completionCancellationRegistry = new CompletionCancellationRegistry();

            ExecutionContext newExecutionContext = executionContext.transform(builder ->
                    builder.errors(Collections.emptyList())
            );

            ExecutionStrategyParameters callParameters = parameters.transform(builder ->
                    builder.deferredErrorSupport(errorSupport)
                            .field(currentField)
                            .fields(fields)
                            .parent(null) // this is a break in the parent -> child chain - its a new start effectively
                            .listSize(0)
                            .currentListIndex(0)
                            .completionCancellationRegistry(completionCancellationRegistry)
            );

            DeferredCall call = new DeferredCall(deferredExecutionResult(newExecutionContext, completionCancellationRegistry, callParameters), errorSupport);
            deferSupport.enqueue(call);
            return true;
        }
        return false;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private Supplier<CompletableFuture<ExecutionResult>> deferredExecutionResult(ExecutionContext executionContext, CompletionCancellationRegistry completionCancellationRegistry, ExecutionStrategyParameters parameters) {
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
                if (throwable != null) {
                    completionCancellationRegistry.dispatch();
                }
                fieldCtx.onFieldValueInfo(fieldValueInfo);

                CompletableFuture<ExecutionResult> execResultFuture = fieldValueInfo.getFieldValue();
                execResultFuture = execResultFuture.whenComplete(fieldCtx::onCompleted);
                if (executionContext.getLazySupport().lazyFieldsDetected()) {
                    execResultFuture = execResultFuture.thenApply(innerResult -> new LazyExecutionResult(innerResult, executionContext.getErrors(), null));
                }
                Async.copyResults(execResultFuture, result);
            });
            return result;
        };
    }
}
