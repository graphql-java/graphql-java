package graphql.execution.preparsed

import java.util.function.Function


class TestingPreparsedDocumentProvider implements PreparsedDocumentProvider {
    private Map<String, PreparsedDocumentEntry> cache = new HashMap<>()

    @Override
    PreparsedDocumentEntry get(String query, Function<String, PreparsedDocumentEntry> compute) {
        return cache.computeIfAbsent(query, compute)
    }

}
