package graphql.execution.preparsed.persisted;

import graphql.PublicApi;
import graphql.execution.preparsed.PreparsedDocumentEntry;

import java.util.function.Function;

/**
 * The call back when a valid persisted query is not in cache and it needs to be compiled and validated
 * by the graphql engine.  If you get a cache miss in your {@link graphql.execution.preparsed.persisted.PersistedQueryCache} implementation
 * then you are required to call back on the provided instance of this interface
 */
@PublicApi
public interface PersistedQueryCacheMiss extends Function<String, PreparsedDocumentEntry> {
    /**
     * You give back the missing query text and graphql-java will compile and validate it.
     *
     * @param queryToBeParsedAndValidated the query text to be parsed and validated
     * @return a parsed and validated query document ready for caching
     */
    @Override
    PreparsedDocumentEntry apply(String queryToBeParsedAndValidated);
}
