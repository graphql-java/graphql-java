package graphql.execution.preparsed


class TestingPreparsedDocumentProvider implements PreparsedDocumentProvider{
    private Map<String, PreparsedDocumentEntry> cache = new HashMap<>()
    @Override
    PreparsedDocumentEntry get(String query) {
        return cache.get(query)
    }

    @Override
    void put(String query, PreparsedDocumentEntry entry) {
        cache.put(query, entry)
    }
}
