package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@PublicApi
public class InstrumentationValidationParameters extends InstrumentationExecutionParameters {
    private final Document document;

    public InstrumentationValidationParameters(ExecutionInput executionInput, Document document, GraphQLSchema schema, InstrumentationState instrumentationState) {
        super(executionInput, schema, instrumentationState);
        this.document = document;
    }

    /**
     * Returns a cloned parameters object with the new state
     *
     * @param instrumentationState the new state for this parameters object
     *
     * @return a new parameters object with the new state
     *
     * @deprecated state is now passed in direct to instrumentation methods
     */
    @Deprecated(since = "2022-07-26")
    @Override
    public InstrumentationValidationParameters withNewState(InstrumentationState instrumentationState) {
        return new InstrumentationValidationParameters(
                this.getExecutionInput(), document, getSchema(), instrumentationState);
    }


    public Document getDocument() {
        return document;
    }
}
