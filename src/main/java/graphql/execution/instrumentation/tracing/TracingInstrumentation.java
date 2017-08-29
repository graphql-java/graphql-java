package graphql.execution.instrumentation.tracing;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.NoOpInstrumentation.NoOpInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.validation.ValidationError;

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
    public InstrumentationState createState() {
        return new TracingSupport();
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(CompletableFuture<ExecutionResult> executionResultFuture, InstrumentationExecutionParameters parameters) {
        return executionResultFuture.thenApply(er -> {
            TracingSupport tracingSupport = parameters.getInstrumentationState();
            Map<Object, Object> tracingMap = new LinkedHashMap<>();
            tracingMap.put("tracing", tracingSupport.snapshotTracingData());
            return new ExecutionResultImpl(er.getData(), er.getErrors(), tracingMap);
        });
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
        return new NoOpInstrumentationContext<>();
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
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
