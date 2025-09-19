package graphql.execution.preparsed.caching;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;

@PublicApi
@NullMarked
public class CaffeineDocumentCacheOptions {

    /**
     * By default, we cache documents for 5 minutes
     */
    public static final Duration EXPIRED_AFTER_ACCESS = Duration.ofMinutes(5);
    /**
     * By default, we hold 1000 entries
     */
    public static final int MAX_SIZE = 1000;

    private static CaffeineDocumentCacheOptions defaultJvmOptions = newOptions()
            .expireAfterAccess(EXPIRED_AFTER_ACCESS)
            .maxSize(MAX_SIZE)
            .build();

    /**
     * This returns the JVM wide default options for the {@link CaffeineDocumentCache}
     *
     * @return the JVM wide default options
     */
    public static CaffeineDocumentCacheOptions getDefaultJvmOptions() {
        return defaultJvmOptions;
    }

    /**
     * This sets new JVM wide default options for the {@link CaffeineDocumentCache}
     *
     * @param jvmOptions
     */
    public static void setDefaultJvmOptions(CaffeineDocumentCacheOptions jvmOptions) {
        defaultJvmOptions = jvmOptions;
    }

    private final Duration expireAfterAccess;
    private final int maxSize;

    private CaffeineDocumentCacheOptions(Builder builder) {
        this.expireAfterAccess = builder.expireAfterAccess;
        this.maxSize = builder.maxSize;
    }

    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public static Builder newOptions() {
        return new Builder();
    }

    public static class Builder {
        Duration expireAfterAccess = Duration.ofMinutes(5);
        int maxSize = 1000;

        public Builder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public Builder expireAfterAccess(Duration expireAfterAccess) {
            this.expireAfterAccess = expireAfterAccess;
            return this;
        }

        CaffeineDocumentCacheOptions build() {
            return new CaffeineDocumentCacheOptions(this);
        }
    }
}
