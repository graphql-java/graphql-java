package graphql.execution.preparsed;


public class NoOpPreparsedDocumentProvider implements PreparsedDocumentProvider {
    public static final NoOpPreparsedDocumentProvider INSTANCE = new NoOpPreparsedDocumentProvider();

    @Override
    public PreparsedDocumentEntry get(String query) {
        return null;
    }

    @Override
    public void put(String query, PreparsedDocumentEntry entry) {

    }
}
