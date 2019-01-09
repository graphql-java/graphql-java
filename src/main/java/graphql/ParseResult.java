package graphql;

import graphql.execution.instrumentation.DocumentAndVariables;
import graphql.language.Document;

import java.util.Map;

@Internal
public class ParseResult {
    private final DocumentAndVariables documentAndVariables;
    private final Exception exception;

    private ParseResult(DocumentAndVariables documentAndVariables, Exception exception) {
        this.documentAndVariables = documentAndVariables;
        this.exception = exception;
    }

    public boolean isFailure() {
        return documentAndVariables == null;
    }

    public Document getDocument() {
        return documentAndVariables.getDocument();
    }

    public Map<String, Object> getVariables() {
        return documentAndVariables.getVariables();
    }

    public Exception getException() {
        return exception;
    }

    public static ParseResult of(DocumentAndVariables document) {
        return new ParseResult(document, null);
    }

    public static ParseResult ofError(Exception e) {
        return new ParseResult(null, e);
    }
}
