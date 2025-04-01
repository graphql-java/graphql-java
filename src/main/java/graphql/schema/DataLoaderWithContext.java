package graphql.schema;

import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.dataloader.PerLevelDataLoaderDispatchStrategy;
import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DispatchResult;
import org.dataloader.ValueCache;
import org.dataloader.stats.Statistics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static graphql.execution.Execution.EXECUTION_CONTEXT_KEY;

public class DataLoaderWithContext<K, V> extends DataLoader<K, V> {
    final DataFetchingEnvironment dfe;
    final String dataLoaderName;
    final DataLoader<K, V> delegate;

    public DataLoaderWithContext(DataFetchingEnvironment dfe, String dataLoaderName, DataLoader<K, V> delegate) {
        super(null);
        this.dataLoaderName = dataLoaderName;
        this.dfe = dfe;
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<V> load(K key) {
        // inform DispatchingStrategy that we are having a DataLoader in DFE
        ExecutionContext executionContext = dfe.getGraphQlContext().get(EXECUTION_CONTEXT_KEY);
        DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = executionContext.getDataLoaderDispatcherStrategy();
        if (dataLoaderDispatcherStrategy instanceof PerLevelDataLoaderDispatchStrategy) {
            ((PerLevelDataLoaderDispatchStrategy) dataLoaderDispatcherStrategy).newDataLoaderCF(new PerLevelDataLoaderDispatchStrategy.DFEWithDataLoader(dfe, delegate));
        }
        return delegate.load(key);
    }

    @Override
    public CompletableFuture<V> load(K key, Object keyContext) {
        CompletableFuture<V> load = delegate.load(key, keyContext);
        return load;
    }

    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys) {
        return delegate.loadMany(keys);
    }

    @Override
    public CompletableFuture<Map<K, V>> loadMany(Map<K, ?> keysAndContexts) {
        return delegate.loadMany(keysAndContexts);
    }

    @Override
    public CompletableFuture<List<V>> dispatch() {
        return delegate.dispatch();
    }

    @Override
    public DispatchResult<V> dispatchWithCounts() {
        return delegate.dispatchWithCounts();
    }

    @Override
    public List<V> dispatchAndJoin() {
        return delegate.dispatchAndJoin();
    }

    @Override
    public int dispatchDepth() {
        return delegate.dispatchDepth();
    }

    @Override
    public DataLoader<K, V> clear(K key) {
        return delegate.clear(key);
    }

    @Override
    public DataLoader<K, V> clear(K key, BiConsumer<Void, Throwable> handler) {
        return delegate.clear(key, handler);
    }

    @Override
    public DataLoader<K, V> clearAll() {
        return delegate.clearAll();
    }

    @Override
    public DataLoader<K, V> clearAll(BiConsumer<Void, Throwable> handler) {
        return delegate.clearAll(handler);
    }

    @Override
    public DataLoader<K, V> prime(K key, V value) {
        return delegate.prime(key, value);
    }

    @Override
    public DataLoader<K, V> prime(K key, Exception error) {
        return delegate.prime(key, error);
    }

    @Override
    public DataLoader<K, V> prime(K key, CompletableFuture<V> value) {
        return delegate.prime(key, value);
    }

    @Override
    public Object getCacheKey(K key) {
        return delegate.getCacheKey(key);
    }

    @Override
    public Statistics getStatistics() {
        return delegate.getStatistics();
    }

    @Override
    public CacheMap<Object, V> getCacheMap() {
        return delegate.getCacheMap();
    }

    @Override
    public ValueCache<K, V> getValueCache() {
        return delegate.getValueCache();
    }
}
