package graphql.execution.preparsed.persisted;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A PersistedQueryCache that is just an in memory map of known queries.
 */
@NullMarked
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
    public CompletableFuture<PreparsedDocumentEntry> getPersistedQueryDocumentAsync(Object persistedQueryId, ExecutionInput executionInput, PersistedQueryCacheMiss onCacheMiss) throws PersistedQueryNotFound {
        PreparsedDocumentEntry documentEntry = cache.compute(persistedQueryId, (k, v) -> {
            if (v != null) {
                return v;
            }

            String queryText = getQueryText(persistedQueryId, executionInput);
            if (queryText == null) {
                throw new PersistedQueryNotFound(persistedQueryId);
            }
            return onCacheMiss.apply(queryText);
        });
        return CompletableFuture.completedFuture(documentEntry);
    }

    private @Nullable String getQueryText(Object persistedQueryId, ExecutionInput executionInput) {
        // the known queries are the source of truth for an id, so they take precedence over the
        // query text in the execution input. Only an id that is not known can be registered from the input.
        String knownQueryText = knownQueries.get(persistedQueryId);
        if (knownQueryText != null) {
            return knownQueryText;
        }

        //get the query from the execution input. Make sure it's not null, empty or the APQ marker.
        String queryText = executionInput.getQuery();
        if (queryText == null || queryText.isEmpty() || queryText.equals(PersistedQuerySupport.PERSISTED_QUERY_MARKER)) {
            return null;
        }
        return queryText;
    }

    public static Builder newInMemoryPersistedQueryCache() {
        return new Builder();
    }

    @NullUnmarked
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
