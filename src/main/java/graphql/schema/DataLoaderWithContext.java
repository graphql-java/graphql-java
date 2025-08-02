package graphql.schema;

import graphql.Internal;
import graphql.execution.incremental.AlternativeCallContext;
import graphql.execution.instrumentation.dataloader.PerLevelDataLoaderDispatchStrategy;
import org.dataloader.DataLoader;
import org.dataloader.DelegatingDataLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
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
        DataFetchingEnvironmentImpl.DFEInternalState dfeInternalState = (DataFetchingEnvironmentImpl.DFEInternalState) dfeImpl.toInternal();
        dfeInternalState.getProfiler().dataLoaderUsed(dataLoaderName);
        if (dfeInternalState.getDataLoaderDispatchStrategy() instanceof PerLevelDataLoaderDispatchStrategy) {
            AlternativeCallContext alternativeCallContext = dfeInternalState.getDeferredCallContext();
            int level = dfe.getExecutionStepInfo().getPath().getLevel();
            String path = dfe.getExecutionStepInfo().getPath().toString();
            ((PerLevelDataLoaderDispatchStrategy) dfeInternalState.dataLoaderDispatchStrategy).newDataLoaderInvocation(path, level, delegate, dataLoaderName, key, alternativeCallContext);
        }
        return result;
    }

    @Override
    public CompletableFuture<List<V>> dispatch() {
        CompletableFuture<List<V>> dispatchResult = delegate.dispatch();
        dispatchResult.whenComplete((result, error) -> {
            if (result != null && result.size() > 0) {
                DataFetchingEnvironmentImpl.DFEInternalState dfeInternalState = (DataFetchingEnvironmentImpl.DFEInternalState) dfe.toInternal();
                dfeInternalState.getProfiler().manualDispatch(dataLoaderName, dfe.getExecutionStepInfo().getPath().getLevel(), result.size());
            }
        });
        return dispatchResult;
    }
}
