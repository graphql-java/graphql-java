package graphql.schema;

import com.google.common.collect.Maps;
import graphql.Internal;
import graphql.execution.Async;
import graphql.execution.incremental.AlternativeCallContext;
import graphql.execution.instrumentation.dataloader.ExhaustedDataLoaderDispatchStrategy;
import graphql.execution.instrumentation.dataloader.PerLevelDataLoaderDispatchStrategy;
import org.dataloader.DataLoader;
import org.dataloader.DelegatingDataLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static graphql.Assert.assertNotNull;

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

    @Override
    public CompletableFuture<V> load(@NonNull K key, @Nullable Object keyContext) {
        // calling super.load() is important, because otherwise the data loader will sometimes called
        // later than the dispatch, which results in a hanging DL
        CompletableFuture<V> result = super.load(key, keyContext);
        newDataLoaderInvocation();
        return result;
    }


    @Override
    public CompletableFuture<List<V>> loadMany(List<K> keys, List<Object> keyContexts) {
        assertNotNull(keys);
        assertNotNull(keyContexts);

        CompletableFuture<List<V>> result;
        List<CompletableFuture<V>> collect = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            K key = keys.get(i);
            Object keyContext = null;
            if (i < keyContexts.size()) {
                keyContext = keyContexts.get(i);
            }
            collect.add(delegate.load(key, keyContext));
        }
        result = Async.allOf(collect);
        newDataLoaderInvocation();
        return result;
    }

    @Override
    public CompletableFuture<Map<K, V>> loadMany(Map<K, ?> keysAndContexts) {
        assertNotNull(keysAndContexts);

        CompletableFuture<Map<K, V>> result;
        Map<K, CompletableFuture<V>> collect = Maps.newHashMapWithExpectedSize(keysAndContexts.size());
        for (Map.Entry<K, ?> entry : keysAndContexts.entrySet()) {
            K key = entry.getKey();
            Object keyContext = entry.getValue();
            collect.put(key, delegate.load(key, keyContext));
        }
        result = Async.allOf(collect);
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
