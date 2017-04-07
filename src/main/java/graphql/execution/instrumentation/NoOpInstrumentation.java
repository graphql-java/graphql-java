package graphql.execution.instrumentation;

import graphql.ExecutionResult;
import graphql.execution.instrumentation.parameters.DataFetchParameters;
import graphql.execution.instrumentation.parameters.ExecutionParameters;
import graphql.execution.instrumentation.parameters.FieldFetchParameters;
import graphql.execution.instrumentation.parameters.FieldParameters;
import graphql.execution.instrumentation.parameters.ValidationParameters;
import graphql.language.Document;
import graphql.validation.ValidationError;

import java.util.List;

/**
 * Nothing to see or do here ;)
 */
public final class NoOpInstrumentation implements Instrumentation {

    public static NoOpInstrumentation INSTANCE = new NoOpInstrumentation();

    private NoOpInstrumentation() {
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(ExecutionParameters parameters) {
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
    public InstrumentationContext<Document> beginParse(ExecutionParameters parameters) {
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
    public InstrumentationContext<List<ValidationError>> beginValidation(ValidationParameters parameters) {
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
    public InstrumentationContext<ExecutionResult> beginDataFetch(DataFetchParameters parameters) {
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
    public InstrumentationContext<ExecutionResult> beginField(FieldParameters parameters) {
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
    public InstrumentationContext<Object> beginFieldFetch(FieldFetchParameters parameters) {
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
