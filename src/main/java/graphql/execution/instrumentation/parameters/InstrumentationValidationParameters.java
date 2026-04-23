package graphql.execution.instrumentation.parameters;

import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.instrumentation.Instrumentation;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.NullMarked;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
@NullMarked
@PublicApi
public class InstrumentationValidationParameters extends InstrumentationExecutionParameters {
    private final Document document;

    public InstrumentationValidationParameters(ExecutionInput executionInput, Document document, GraphQLSchema schema) {
        super(executionInput, schema);
        this.document = document;
    }


    public Document getDocument() {
        return document;
    }
}
