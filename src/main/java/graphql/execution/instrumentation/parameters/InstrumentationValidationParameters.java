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

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    public InstrumentationValidationParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationValidationParameters(
                this.getExecutionInput(), document, instrumentationState);
    }


    public Document getDocument() {
        return document;
    }
}
