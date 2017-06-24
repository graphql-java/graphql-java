package graphql.execution.preparsed;


/**
 * Interface that allows clients to hook in Document caching and/or whitelisting of queries
 */
public interface PreparsedDocumentProvider {
    /**
     * Get existing instance of a preparsed query
     *
     * @param query The graphql query
     * @return Null of missing or an instance of {@link PreparsedDocumentEntry}
     */
    PreparsedDocumentEntry get(String query);

    /**
     * Put the parse and validate result into the provider
     *
     * @param query The graphql query
     * @param entry The result of parse and validation of the query
     */
    void put(String query, PreparsedDocumentEntry entry);
}


