package graphql.execution.preparsed;


import java.util.function.Function;

/**
 * Interface that allows clients to hook in Document caching and/or whitelisting of queries
 */
@FunctionalInterface
public interface PreparsedDocumentProvider {
    /**
     * Get existing instance of a preparsed query
     *
     * @param query The graphql query
     * @return Null of missing or an instance of {@link PreparsedDocumentEntry}
     */
    PreparsedDocumentEntry get(String query, Function<String, PreparsedDocumentEntry> compute);
}


