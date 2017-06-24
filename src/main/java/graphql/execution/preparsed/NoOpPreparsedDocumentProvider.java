package graphql.execution.preparsed;


import java.util.function.Function;

public class NoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {
    public static final NoOpPreparsedDocumentProvider INSTANCE = new NoOpPreparsedDocumentProvider();

    @Override
    public PreparsedDocumentEntry get(String query, Function<String, PreparsedDocumentEntry> compute) {
        return compute.apply(query);
    }
}
