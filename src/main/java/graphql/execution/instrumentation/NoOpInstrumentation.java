package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.InstrumentationDataFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.language.Document;
import graphql.validation.ValidationError;

import java.util.List;

/**
 * Nothing to see or do here ;)
 */
public final class NoOpInstrumentation implements Instrumentation {

    public static final NoOpInstrumentation INSTANCE = new NoOpInstrumentation();

    private NoOpInstrumentation() {
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

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return new NoOpInstrumentationContext<>();
    }

    public static class NoOpInstrumentationContext<T> implements InstrumentationContext<T> {
        @Override
        public void onEnd(T result) {
        }

        @Override
        public void onEnd(Throwable t) {
        }
    }
}
