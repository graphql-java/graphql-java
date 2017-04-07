package graphql.execution.instrumentation.parameters;

import graphql.execution.instrumentation.Instrumentation;
import graphql.language.Document;

import java.util.Map;

/**
 * Parameters sent to {@link Instrumentation} methods
 */
public class ValidationParameters extends ExecutionParameters {
    private final Document document;

    public ValidationParameters(String query, String operation, Object context, Map<String, Object> arguments, Document document) {
        super(query, operation, context, arguments);
        this.document = document;
    }

    public Document getDocument() {
        return document;
    }
}
