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
        return new InstrumentationContext<ExecutionResult>() {
            @Override
            public void onEnd(ExecutionResult result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
        return new InstrumentationContext<Document>() {
            @Override
            public void onEnd(Document result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
        return new InstrumentationContext<List<ValidationError>>() {
            @Override
            public void onEnd(List<ValidationError> result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
        return new InstrumentationContext<ExecutionResult>() {
            @Override
            public void onEnd(ExecutionResult result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
        return new InstrumentationContext<ExecutionResult>() {
            @Override
            public void onEnd(ExecutionResult result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }

    @Override
    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
        return new InstrumentationContext<Object>() {
            @Override
            public void onEnd(Object result) {
            }

            @Override
            public void onEnd(Exception e) {
            }
        };
    }
}
