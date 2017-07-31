package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.Document;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationValidationParameters extends InstrumentationExecutionParameters {
    private final Document document;

    public InstrumentationValidationParameters(ExecutionInput executionInput, Document document, InstrumentationState instrumentationState) {
        super(executionInput, instrumentationState);
        this.document = document;
    }

    public Document getDocument() {
        return document;
    }
}
