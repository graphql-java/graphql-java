package graphql.execution.instrumentation.dataloader;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.schema.DataFetchingEnvironment;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static graphql.execution.Execution.EXECUTION_CONTEXT_KEY;


@Internal
public class DataLoaderCF<T> extends CompletableFuture<T> {
    final DataFetchingEnvironment dfe;
    final String dataLoaderName;
    final Object key;
    private final CompletableFuture<Object> underlyingDataLoaderCompletableFuture;

    final CompletableFuture<Void> finishedSyncDependents = new CompletableFuture<>();

    public DataLoaderCF(DataFetchingEnvironment dfe, String dataLoaderName, Object key) {
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
                finishedSyncDependents.complete(null);
            });
        } else {
            underlyingDataLoaderCompletableFuture = null;
        }
    }

    DataLoaderCF() {
        this.dfe = null;
        this.dataLoaderName = null;
        this.key = null;
        underlyingDataLoaderCompletableFuture = null;
    }


    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        return new DataLoaderCF<>();
    }

    public static boolean isDataLoaderCF(Object object) {
        return object instanceof DataLoaderCF;
    }

    @ExperimentalApi
    public static <T> CompletableFuture<T> newDataLoaderCF(DataFetchingEnvironment dfe, String dataLoaderName, Object key) {
        DataLoaderCF<T> result = new DataLoaderCF<>(dfe, dataLoaderName, key);
        ExecutionContext executionContext = dfe.getGraphQlContext().get(EXECUTION_CONTEXT_KEY);
        DataLoaderDispatchStrategy dataLoaderDispatcherStrategy = executionContext.getDataLoaderDispatcherStrategy();
        if (dataLoaderDispatcherStrategy instanceof PerLevelDataLoaderDispatchStrategy) {
            ((PerLevelDataLoaderDispatchStrategy) dataLoaderDispatcherStrategy).newDataLoaderCF(result);
        }
        return result;
    }


    @ExperimentalApi
    public static <U> CompletableFuture<U> supplyAsyncDataLoaderCF(DataFetchingEnvironment env, Supplier<U> supplier) {
        DataLoaderCF<U> d = new DataLoaderCF<>(env, null, null);
        d.defaultExecutor().execute(() -> {
            d.complete(supplier.get());
        });
        return d;

    }

    @ExperimentalApi
    public static <U> CompletableFuture<U> wrap(DataFetchingEnvironment env, CompletableFuture<U> completableFuture) {
        DataLoaderCF<U> d = new DataLoaderCF<>(env, null, null);
        completableFuture.whenComplete((u, ex) -> {
            if (ex != null) {
                d.completeExceptionally(ex);
            } else {
                d.complete(u);
            }
        });
        return d;
    }

    public static CompletableFuture<Void> waitUntilAllSyncDependentsComplete(List<DataLoaderCF<?>> dataLoaderCFList) {
        CompletableFuture<?>[] finishedSyncArray = dataLoaderCFList
                .stream()
                .map(dataLoaderCF -> dataLoaderCF.finishedSyncDependents)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(finishedSyncArray);
    }


}

