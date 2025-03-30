package graphql.execution.instrumentation.dataloader;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.schema.DataFetchingEnvironment;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static graphql.execution.Execution.EXECUTION_CONTEXT_KEY;

@Internal
public class DataLoaderCF<T> extends CompletableFuture<T> {
    final DataFetchingEnvironment dfe;
    final String dataLoaderName;
    final Object key;
    final CompletableFuture<Object> dataLoaderCF;

    final CompletableFuture<Void> finishedSyncDependents = new CompletableFuture<Void>();

    public DataLoaderCF(DataFetchingEnvironment dfe, String dataLoaderName, Object key) {
        this.dfe = dfe;
        this.dataLoaderName = dataLoaderName;
        this.key = key;
        if (dataLoaderName != null) {
            dataLoaderCF = dfe.getDataLoaderRegistry().getDataLoader(dataLoaderName).load(key);
            dataLoaderCF.whenComplete((value, throwable) -> {
                System.out.println("underlying DataLoader completed");
                if (throwable != null) {
                    completeExceptionally(throwable);
                } else {
                    complete((T) value);
                }
                // post completion hook
                finishedSyncDependents.complete(null);
            });
        } else {
            dataLoaderCF = null;
        }
    }

    DataLoaderCF() {
        this.dfe = null;
        this.dataLoaderName = null;
        this.key = null;
        dataLoaderCF = null;
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


}
