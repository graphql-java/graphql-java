package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.ExecutionResult;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.introspection.Introspection;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static graphql.execution.instrumentation.SimpleInstrumentationContext.nonNullCtx;

/**
 * Async non-blocking execution, but serial: only one field at the time will be resolved.
 * See {@link AsyncExecutionStrategy} for a non-serial (parallel) execution of every field.
 */
@PublicApi
public class AsyncSerialExecutionStrategy extends AbstractAsyncExecutionStrategy {

    public AsyncSerialExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    public AsyncSerialExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    @SuppressWarnings({"TypeParameterUnusedInFormals", "FutureReturnValueIgnored"})
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        executionContext.running();
        DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = executionContext.getDataLoaderDispatcherStrategy();

        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);
        InstrumentationContext<ExecutionResult> executionStrategyCtx = nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters,
                executionContext.getInstrumentationState())
        );
        MergedSelectionSet fields = parameters.getFields();
        ImmutableList<String> fieldNames = ImmutableList.copyOf(fields.keySet());

        // this is highly unlikely since Mutations cant do introspection BUT in theory someone could make the query strategy this code
        // so belts and braces
        Optional<ExecutionResult> isNotSensible = Introspection.isIntrospectionSensible(fields, executionContext);
        if (isNotSensible.isPresent()) {
            executionContext.finished();
            return CompletableFuture.completedFuture(isNotSensible.get());
        }

        CompletableFuture<List<Object>> resultsFuture = Async.eachSequentially(fieldNames, (fieldName, prevResults) -> {
            executionContext.running();
            MergedField currentField = fields.getSubField(fieldName);
            ResultPath fieldPath = parameters.getPath().segment(mkNameForPath(currentField));
            ExecutionStrategyParameters newParameters = parameters
                    .transform(builder -> builder.field(currentField).path(fieldPath));

            Object resolveSerialField = resolveSerialField(executionContext, dataLoaderDispatcherStrategy, newParameters);
            executionContext.finished();
            return resolveSerialField;
        });

        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched();

        resultsFuture.whenComplete(handleResults(executionContext, fieldNames, overallResult));
        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        executionContext.finished();
        return overallResult;
    }

    private Object resolveSerialField(ExecutionContext executionContext,
                                      DataLoaderDispatchStrategy dataLoaderDispatcherStrategy,
                                      ExecutionStrategyParameters newParameters) {
        dataLoaderDispatcherStrategy.executionSerialStrategy(executionContext, newParameters);

        Object fieldWithInfo = resolveFieldWithInfo(executionContext, newParameters);
        if (fieldWithInfo instanceof CompletableFuture) {
            //noinspection unchecked
            return ((CompletableFuture<FieldValueInfo>) fieldWithInfo).thenCompose(fvi -> {
                executionContext.running();
                dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesInfo(List.of(fvi));
                CompletableFuture<Object> fieldValueFuture = fvi.getFieldValueFuture();
                executionContext.finished();
                return fieldValueFuture;
            });
        } else {
            FieldValueInfo fvi = (FieldValueInfo) fieldWithInfo;
            dataLoaderDispatcherStrategy.executionStrategyOnFieldValuesInfo(List.of(fvi));
            return fvi.getFieldValueObject();
        }
    }
}
