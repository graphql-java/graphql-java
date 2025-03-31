package graphql.schema;

import graphql.PublicApi;
import graphql.execution.instrumentation.dataloader.DataLoaderCompletableFuture;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@PublicApi
@NullMarked
public class DataLoaderChain {

    private final DataFetchingEnvironment dfe;

    public DataLoaderChain(DataFetchingEnvironment dfe) {
        this.dfe = dfe;
    }

    public <T> CompletableFuture<T> load(String dataLoaderName, Object key) {
        return DataLoaderCompletableFuture.newDLCF(dfe, dataLoaderName, key);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return supplyAsync(supplier, null);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, @Nullable Executor executor) {
        return DataLoaderCompletableFuture.supplyDLCF(dfe, supplier, executor);
    }

    public <T> CompletableFuture<T> wrap(CompletableFuture<T> future) {
        return DataLoaderCompletableFuture.wrap(dfe, future);
    }
}
