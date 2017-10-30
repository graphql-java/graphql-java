package graphql.execution.instrumentation.tracing;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationPreExecutionState;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.NoOpInstrumentation.NoOpInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationCreatePreExecutionStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.validation.ValidationError;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This {@link Instrumentation} implementation uses {@link TracingSupport} to
 * capture tracing information and puts it into the {@link ExecutionResult}
 */
@PublicApi
public class TracingInstrumentation implements Instrumentation {

    @Override
    public InstrumentationPreExecutionState createPreExecutionState(InstrumentationCreatePreExecutionStateParameters parameters) {
        return new TracingSupport();
    }

    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return parameters.getInstrumentationState();
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentFinalExecutionResult(ExecutionResult executionResult, InstrumentationExecutionParameters parameters) {
        Map<Object, Object> currentExt = executionResult.getExtensions();

        TracingSupport tracingSupport = parameters.getInstrumentationState();
        Map<Object, Object> tracingMap = new LinkedHashMap<>();
        tracingMap.putAll(currentExt == null ? Collections.emptyMap() : currentExt);
        tracingMap.put("tracing", tracingSupport.snapshotTracingData());

        return CompletableFuture.completedFuture(new ExecutionResultImpl(executionResult.getData(), executionResult.getErrors(), tracingMap));
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        TracingSupport tracingSupport = parameters.getInstrumentationState();
        TracingSupport.TracingContext ctx = tracingSupport.beginField(parameters.getEnvironment());
        return (result, t) -> ctx.onEnd();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        TracingSupport tracingSupport = parameters.getInstrumentationState();
        TracingSupport.TracingContext ctx = tracingSupport.beginParse();
        return (result, t) -> ctx.onEnd();
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        TracingSupport tracingSupport = parameters.getInstrumentationState();
        TracingSupport.TracingContext ctx = tracingSupport.beginValidation();
        return (result, t) -> ctx.onEnd();
    }

    @Override
    public InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }
}
