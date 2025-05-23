package graphql.schema;

import graphql.Internal;
import graphql.execution.incremental.DeferredCallContext;
import graphql.execution.instrumentation.dataloader.PerLevelDataLoaderDispatchStrategy;
import org.dataloader.DataLoader;
import org.dataloader.DelegatingDataLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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

    @Override
    public CompletableFuture<V> load(@NonNull K key, @Nullable Object keyContext) {
        // calling super.load() is important, because otherwise the data loader will sometimes called
        // later than the dispatch, which results in a hanging DL
        CompletableFuture<V> result = super.load(key, keyContext);
        DataFetchingEnvironmentImpl dfeImpl = (DataFetchingEnvironmentImpl) dfe;
        int level = dfe.getExecutionStepInfo().getPath().getLevel();
        String path = dfe.getExecutionStepInfo().getPath().toString();
        DataFetchingEnvironmentImpl.DFEInternalState dfeInternalState = (DataFetchingEnvironmentImpl.DFEInternalState) dfeImpl.toInternal();
        dfeInternalState.getProfiler().dataLoaderUsed(dataLoaderName);
        if (dfeInternalState.getDataLoaderDispatchStrategy() instanceof PerLevelDataLoaderDispatchStrategy) {
            DeferredCallContext deferredCallContext = dfeInternalState.getDeferredCallContext();
            ((PerLevelDataLoaderDispatchStrategy) dfeInternalState.dataLoaderDispatchStrategy).newDataLoaderLoadCall(path, level, delegate, dataLoaderName, key, deferredCallContext);
        }
        return result;
    }

}
