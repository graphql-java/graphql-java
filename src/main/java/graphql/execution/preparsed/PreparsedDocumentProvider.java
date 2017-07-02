package graphql.execution.preparsed;


import java.util.function.Function;

/**
 * Interface that allows clients to hook in Document caching and/or the whitelisting of queries
 */
@FunctionalInterface
public interface PreparsedDocumentProvider {
    /**
     * This is called to get a "cached" pre-parsed query and if its not present, then the computeFunction
     * can be called to parse the query
     *
     * @param query           The graphql query
     * @param computeFunction If the query has not be pre-parsed, this function can be called to parse it
     *
     * @return an instance of {@link PreparsedDocumentEntry}
     */
    PreparsedDocumentEntry get(String query, Function<String, PreparsedDocumentEntry> computeFunction);
}


