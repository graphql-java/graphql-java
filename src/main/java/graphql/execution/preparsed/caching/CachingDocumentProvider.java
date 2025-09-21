package graphql.execution.preparsed.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * By default, graphql-java will cache the parsed {@link PreparsedDocumentEntry} that represents
 * a parsed and validated graphql query IF {@link Caffeine} is present on the class path
 * at runtime.  If it's not then no caching takes place.
 */
@PublicApi
@NullMarked
public class CachingDocumentProvider implements PreparsedDocumentProvider {
    private final DocumentCache documentCache;

    /**
     * By default, it will try to use a {@link Caffeine} backed implementation if it's on the class
     * path otherwise it will become a non caching mechanism.
     */
    public CachingDocumentProvider() {
        this(new CaffeineDocumentCache());
    }

    /**
     * You can use your own cache implementation and provide that to this class to use
     *
     * @param documentCache the cache to use
     */
    public CachingDocumentProvider(DocumentCache documentCache) {
        this.documentCache = documentCache;
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        if (documentCache.isNoop()) {
            // saves creating keys and doing a lookup that will just call this function anyway
            return CompletableFuture.completedFuture(parseAndValidateFunction.apply(executionInput));
        }
        DocumentCache.DocumentCacheKey cacheKey = new DocumentCache.DocumentCacheKey(executionInput.getQuery(), executionInput.getOperationName());
        PreparsedDocumentEntry cacheEntry = documentCache.get(cacheKey, key -> parseAndValidateFunction.apply(executionInput));
        return CompletableFuture.completedFuture(cacheEntry);
    }

}
