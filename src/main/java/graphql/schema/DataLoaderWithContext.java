package graphql.schema;

import graphql.Internal;
import graphql.execution.incremental.AlternativeCallContext;
import graphql.execution.instrumentation.dataloader.ExhaustedDataLoaderDispatchStrategy;
import graphql.execution.instrumentation.dataloader.PerLevelDataLoaderDispatchStrategy;
import org.dataloader.DataLoader;
import org.dataloader.DelegatingDataLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Internal
@NullMarked
public class DataLoaderWithContext<K, V> extends DelegatingDataLoader<K, V> {
    final DataFetchingEnvironment dfe;
    final String dataLoaderName;

    public DataLoaderWithContext(DataFetchingEnvironment dfe, String dataLoaderName, DataLoader<K, V> delegate) {
        super(delegate);
        this.dataLoaderName = dataLoaderName;
        this.dfe = dfe;
    }

    // general note: calling super.load() is important, because otherwise the data loader will sometimes called
    // later than the dispatch, which results in a hanging DL

    @Override
    public CompletableFuture<V> load(K key) {
        CompletableFuture<V> result = super.load(key);
        newDataLoaderInvocation();
        return result;
    }

    @Override
    public CompletableFuture<V> load(@NonNull K key, @Nullable Object keyContext) {
        CompletableFuture<V> result = super.load(key, keyContext);
        newDataLoaderInvocation();
        return result;
    }

    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys) {
        CompletableFuture<List<V>> result = super.loadMany(keys);
        newDataLoaderInvocation();
        return result;
    }

    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys, List<Object> keyContexts) {
        CompletableFuture<List<V>> result = super.loadMany(keys, keyContexts);
        newDataLoaderInvocation();
        return result;
    }

    @Override
    public CompletableFuture<Map<K, V>> loadMany(Map<K, ?> keysAndContexts) {
        CompletableFuture<Map<K, V>> result = super.loadMany(keysAndContexts);
        newDataLoaderInvocation();
        return result;
    }

    private void newDataLoaderInvocation() {
        DataFetchingEnvironmentImpl dfeImpl = (DataFetchingEnvironmentImpl) dfe;
        DataFetchingEnvironmentImpl.DFEInternalState dfeInternalState = (DataFetchingEnvironmentImpl.DFEInternalState) dfeImpl.toInternal();
        if (dfeInternalState.getDataLoaderDispatchStrategy() instanceof PerLevelDataLoaderDispatchStrategy) {
            AlternativeCallContext alternativeCallContext = dfeInternalState.getDeferredCallContext();
            int level = dfeImpl.getLevel();
            ((PerLevelDataLoaderDispatchStrategy) dfeInternalState.dataLoaderDispatchStrategy).newDataLoaderInvocation(level, delegate, alternativeCallContext);
        } else if (dfeInternalState.getDataLoaderDispatchStrategy() instanceof ExhaustedDataLoaderDispatchStrategy) {
            AlternativeCallContext alternativeCallContext = dfeInternalState.getDeferredCallContext();
            ((ExhaustedDataLoaderDispatchStrategy) dfeInternalState.dataLoaderDispatchStrategy).newDataLoaderInvocation(alternativeCallContext);
        }
    }


}
