package graphql;

import graphql.language.Document;

@Internal
public class ParseResult {
    private final Document document;
    private final Exception exception;

    public ParseResult(Document document, Exception exception) {
        this.document = document;
        this.exception = exception;
    }

    public boolean isFailure() {
        return document == null;
    }

    public Document getDocument() {
        return document;
    }

    public Exception getException() {
        return exception;
    }

    public static ParseResult of(Document document) {
        return new ParseResult(document, null);
    }

    public static ParseResult ofError(Exception e) {
        return new ParseResult(null, e);
    }
}
