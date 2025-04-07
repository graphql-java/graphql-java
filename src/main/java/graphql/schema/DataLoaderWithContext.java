package graphql.schema;

import graphql.Internal;
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
    final DataLoader<K, V> delegate;

    public DataLoaderWithContext(DataFetchingEnvironment dfe, String dataLoaderName, DataLoader<K, V> delegate) {
        super(delegate);
        this.dataLoaderName = dataLoaderName;
        this.dfe = dfe;
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<V> load(@NonNull K key, @Nullable Object keyContext) {
        DataFetchingEnvironmentImpl dfeImpl = (DataFetchingEnvironmentImpl) dfe;
        int level = dfe.getExecutionStepInfo().getPath().getLevel();
        String path = dfe.getExecutionStepInfo().getPath().toString();
        DataFetchingEnvironmentImpl.DFEInternalState dfeInternalState = (DataFetchingEnvironmentImpl.DFEInternalState) dfeImpl.toInternal();
        if (dfeInternalState.getDataLoaderDispatchStrategy() instanceof PerLevelDataLoaderDispatchStrategy) {
            ((PerLevelDataLoaderDispatchStrategy) dfeInternalState.dataLoaderDispatchStrategy).newDataLoaderLoadCall(path, level, delegate);
        }
        return super.load(key, keyContext);
    }

}
