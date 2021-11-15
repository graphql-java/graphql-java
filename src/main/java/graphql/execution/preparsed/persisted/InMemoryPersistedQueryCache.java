package graphql.execution.preparsed.persisted;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.preparsed.PreparsedDocumentEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A PersistedQueryCache that is just an in memory map of known queries.
 */
@PublicApi
public class InMemoryPersistedQueryCache implements PersistedQueryCache {

    private final Map<Object, PreparsedDocumentEntry> cache = new ConcurrentHashMap<>();
    private final Map<Object, String> knownQueries;

    public InMemoryPersistedQueryCache(Map<Object, String> knownQueries) {
        this.knownQueries = Assert.assertNotNull(knownQueries);
    }

    public Map<Object, String> getKnownQueries() {
        return knownQueries;
    }

    @Override
    public PreparsedDocumentEntry getPersistedQueryDocument(Object persistedQueryId, ExecutionInput executionInput, PersistedQueryCacheMiss onCacheMiss) throws PersistedQueryNotFound {
        return cache.compute(persistedQueryId, (k, v) -> {
            if (v != null) {
                return v;
            }

            //get the query from the execution input. Make sure it's not null, empty or the APQ marker.
            // if it is, fallback to the known queries.
            String queryText = executionInput.getQuery();
            if (queryText == null || queryText.isEmpty() || queryText.equals(PersistedQuerySupport.PERSISTED_QUERY_MARKER)) {
                queryText = knownQueries.get(persistedQueryId);
            }

            if (queryText == null) {
                throw new PersistedQueryNotFound(persistedQueryId);
            }
            return onCacheMiss.apply(queryText);
        });
    }

    public static Builder newInMemoryPersistedQueryCache() {
        return new Builder();
    }

    public static class Builder {
        private final Map<Object, String> knownQueries = new HashMap<>();

        public Builder addQuery(Object key, String queryText) {
            knownQueries.put(key, queryText);
            return this;
        }

        public InMemoryPersistedQueryCache build() {
            return new InMemoryPersistedQueryCache(knownQueries);
        }
    }
}
