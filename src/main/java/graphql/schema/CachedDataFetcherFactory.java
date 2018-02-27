package graphql.schema;

import static graphql.Assert.assertNotNull;

/**
 * Threadsafe Implementation of a cached DataFetcherFactory:
 * The DataFetcherFactory will only be called once!
 * It is a double checked locking implementation: https://en.wikipedia.org/wiki/Double-checked_locking#Usage_in_Java
 */
public class CachedDataFetcherFactory implements DataFetcherFactory {

    private final DataFetcherFactory delegate;
    private volatile DataFetcher dataFetcher;

    public CachedDataFetcherFactory(DataFetcherFactory delegate) {
        this.delegate = assertNotNull(delegate, "delegate DataFetcherFactory can't be null");
    }

    @Override
    public DataFetcher get(DataFetcherFactoryEnvironment environment) {
        DataFetcher result = dataFetcher;
        if (result == null) {
            synchronized (this) {
                result = dataFetcher;
                if (result == null) {
                    result = dataFetcher = this.delegate.get(environment);
                }
            }
        }
        return result;
    }
}
