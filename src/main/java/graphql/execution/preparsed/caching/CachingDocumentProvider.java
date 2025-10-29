package graphql.execution.preparsed.caching;

import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.ExecutionInput;
import graphql.PublicApi;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static graphql.execution.Async.toCompletableFuture;

/**
 * The CachingDocumentProvider allows previously parsed and validated operations to be cached and
 * hence re-used.  This can lead to significant time savings, especially for large operations.
 * <p>
 * By default, graphql-java will cache the parsed {@link PreparsedDocumentEntry} that represents
 * a parsed and validated graphql query IF {@link Caffeine} is present on the class path
 * at runtime.  If it's not then no caching takes place.
 * <p>
 * You can provide your own {@link DocumentCache} implementation and hence use any cache
 * technology you like.
 */
@PublicApi
@NullMarked
public class CachingDocumentProvider implements PreparsedDocumentProvider {
    private final DocumentCache documentCache;

    /**
     * By default, it will try to use a {@link Caffeine} backed implementation if it's on the class
     * path otherwise it will become a non caching mechanism.
     *
     * @see CaffeineDocumentCache
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

    /**
     * @return the {@link DocumentCache} being used
     */
    public DocumentCache getDocumentCache() {
        return documentCache;
    }

    @Override
    public CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
        if (documentCache.isNoop()) {
            // saves creating keys and doing a lookup that will just call this function anyway
            return toCompletableFuture(parseAndValidateFunction.apply(executionInput));
        }
        DocumentCache.DocumentCacheKey cacheKey = new DocumentCache.DocumentCacheKey(executionInput.getQuery(), executionInput.getOperationName(), executionInput.getLocale());
        Object cacheEntry = documentCache.get(cacheKey, key -> parseAndValidateFunction.apply(executionInput));
        return toCompletableFuture(cacheEntry);
    }

}
