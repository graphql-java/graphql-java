package graphql.execution.instrumentation.dataloader;

import graphql.Internal;
import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;


@Internal
@NullMarked
public class DataLoaderCompletableFuture<T> extends CompletableFuture<T> {
    final DataFetchingEnvironment dfe;
    final String dataLoaderName;
    final Object key;
    private final CompletableFuture<Object> underlyingDataLoaderCompletableFuture;

    final CompletableFuture<Void> finishedSyncDependents = new CompletableFuture<>();

    public DataLoaderCompletableFuture(DataFetchingEnvironment dfe, String dataLoaderName, Object key) {
        this.dfe = dfe;
        this.dataLoaderName = dataLoaderName;
        this.key = key;
        if (dataLoaderName != null) {
            underlyingDataLoaderCompletableFuture = dfe.getDataLoaderRegistry().getDataLoader(dataLoaderName).load(key);
            underlyingDataLoaderCompletableFuture.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    completeExceptionally(throwable);
                } else {
                    complete((T) value); // causing all sync dependent code to run
                }
                // post completion hook
                finishedSyncDependents.complete(null); // is the same as dispatch CF returned by DataLoader.dispatch()
            });
        } else {
            underlyingDataLoaderCompletableFuture = null;
        }
    }

    DataLoaderCompletableFuture() {
        this.dfe = null;
        this.dataLoaderName = null;
        this.key = null;
        underlyingDataLoaderCompletableFuture = null;
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new DataLoaderCompletableFuture<>();
    }

    public static boolean isDataLoaderCompletableFuture(Object object) {
        return object instanceof DataLoaderCompletableFuture;
    }

    public static <T> CompletableFuture<T> newDLCF(DataFetchingEnvironment dfe, String dataLoaderName, Object key) {
        throw new UnsupportedOperationException();
//        DataLoaderCompletableFuture<T> result = new DataLoaderCompletableFuture<>(dfe, dataLoaderName, key);
//        ExecutionContext executionContext = dfe.getGraphQlContext().get(EXECUTION_CONTEXT_KEY);
//        DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = executionContext.getDataLoaderDispatcherStrategy();
//        if (dataLoaderDispatcherStrategy instanceof PerLevelDataLoaderDispatchStrategy) {
//            ((PerLevelDataLoaderDispatchStrategy) dataLoaderDispatcherStrategy).newDataLoaderCF(new PerLevelDataLoaderDispatchStrategy.DFEWithDataLoader());
//        }
//        return result;
    }


    public static <U> CompletableFuture<U> supplyDLCF(DataFetchingEnvironment env, Supplier<U> supplier, @Nullable Executor executor) {
        DataLoaderCompletableFuture<U> d = new DataLoaderCompletableFuture<>(env, null, null);
        if (executor == null) {
            executor = d.defaultExecutor();
        }
        executor.execute(() -> {
            d.complete(supplier.get());
        });
        return d;

    }


    public static <U> CompletableFuture<U> wrap(DataFetchingEnvironment env, CompletableFuture<U> completableFuture) {
        if (completableFuture instanceof DataLoaderCompletableFuture) {
            return completableFuture;
        }
        DataLoaderCompletableFuture<U> d = new DataLoaderCompletableFuture<>(env, null, null);
        completableFuture.whenComplete((u, ex) -> {
            if (ex != null) {
                d.completeExceptionally(ex);
            } else {
                d.complete(u);
            }
        });
        return d;
    }

    public static CompletableFuture<Void> waitUntilAllSyncDependentsComplete(List<DataLoaderCompletableFuture<?>> dataLoaderCompletableFutureList) {
        CompletableFuture<?>[] finishedSyncArray = dataLoaderCompletableFutureList
                .stream()
                .map(dataLoaderCompletableFuture -> dataLoaderCompletableFuture.finishedSyncDependents)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(finishedSyncArray);
    }


}

