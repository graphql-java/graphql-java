package graphql.execution.preparsed.caching;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import graphql.PublicApi;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.util.ClassKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

@PublicApi
@NullMarked
public class CaffeineDocumentCache implements DocumentCache {

    private final static boolean isCaffeineAvailable = ClassKit.isClassAvailable("com.github.benmanes.caffeine.cache.Caffeine");

    @Nullable
    private final Object caffeineCacheObj;

    CaffeineDocumentCache(boolean isCaffeineAvailable) {
        if (isCaffeineAvailable) {
            CaffeineDocumentCacheOptions options = CaffeineDocumentCacheOptions.getDefaultJvmOptions();
            caffeineCacheObj = Caffeine.newBuilder()
                    .expireAfterAccess(options.getExpireAfterAccess())
                    .maximumSize(options.getMaxSize())
                    .build();
        } else {
            caffeineCacheObj = null;
        }
    }

    /**
     * Creates a cache that works if Caffeine is on the class path otherwise its
     * a no op.
     */
    public CaffeineDocumentCache() {
        this(isCaffeineAvailable);
    }

    /**
     * If you want to control the {@link Caffeine} configuration, using this constructor and pass in your own {@link Caffeine} cache
     *
     * @param caffeineCache the custom {@link Caffeine} cache to use
     */
    public CaffeineDocumentCache(Cache<DocumentCache.DocumentCacheKey, PreparsedDocumentEntry> caffeineCache) {
        this.caffeineCacheObj = caffeineCache;
    }

    @Override
    public PreparsedDocumentEntry get(DocumentCacheKey key, Function<DocumentCacheKey, PreparsedDocumentEntry> mappingFunction) {
        if (isNoop()) {
            return mappingFunction.apply(key);
        }
        return cache().get(key, mappingFunction);
    }

    private Cache<DocumentCache.DocumentCacheKey, PreparsedDocumentEntry> cache() {
        //noinspection unchecked
        return (Cache<DocumentCacheKey, PreparsedDocumentEntry>) requireNonNull(caffeineCacheObj);
    }

    @Override
    public boolean isNoop() {
        return caffeineCacheObj == null;
    }

    @Override
    public void invalidateAll() {
        if (!isNoop()) {
            cache().invalidateAll();
        }
    }
}
