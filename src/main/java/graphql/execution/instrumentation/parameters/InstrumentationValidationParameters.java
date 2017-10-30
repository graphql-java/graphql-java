package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationPreExecutionState;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class InstrumentationValidationParameters extends InstrumentationExecutionParameters {
    private final Document document;

    public InstrumentationValidationParameters(ExecutionInput executionInput, Document document, GraphQLSchema schema, InstrumentationPreExecutionState instrumentationState) {
        super(executionInput, schema, instrumentationState);
        this.document = document;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     */
    public InstrumentationValidationParameters withNewState(InstrumentationPreExecutionState instrumentationState) {
        return new InstrumentationValidationParameters(
                this.getExecutionInput(), document, getSchema(), instrumentationState);
    }


    public Document getDocument() {
        return document;
    }
}
