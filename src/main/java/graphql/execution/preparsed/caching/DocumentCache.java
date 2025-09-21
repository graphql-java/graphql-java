package graphql.execution.preparsed.caching;

import graphql.PublicApi;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * This represents a cache interface to get a document from a cache key.  You can use your own cache implementation
 * to back the caching of parsed graphql documents.
 */
@PublicApi
@NullMarked
public interface DocumentCache {
    /**
     * Called to get a document that has previously been parsed ad validated.
     *
     * @param key             the cache key
     * @param mappingFunction if the value is missing in cache this function can be called to create a value
     *
     * @return a non null document entry
     */
    PreparsedDocumentEntry get(DocumentCacheKey key, Function<DocumentCacheKey, PreparsedDocumentEntry> mappingFunction);

    /**
     * @return true if the cache in fact does no caching otherwise false.  This helps the implementation optimise how the cache is used or not.
     */
    boolean isNoop();

    /**
     * This represents the key to the document cache
     */
    class DocumentCacheKey {
        private final String query;
        @Nullable
        private final String operationName;

        DocumentCacheKey(String query, @Nullable String operationName) {
            this.query = query;
            this.operationName = operationName;
        }

        public String getQuery() {
            return query;
        }

        public @Nullable String getOperationName() {
            return operationName;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DocumentCacheKey cacheKey = (DocumentCacheKey) o;
            return Objects.equals(query, cacheKey.query) && Objects.equals(operationName, cacheKey.operationName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, operationName);
        }
    }
}
