package graphql.schema;

import static graphql.Assert.assertNotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

import graphql.PublicApi;

/**
 * A modifier type that indicates the underlying data fetcher is run asynchronously
 */
@PublicApi
public class AsynchronousDataFetcher<T> implements DataFetcher<CompletableFuture<T>> {
    
    /**
     * A factory method for creating asynchronous data fetchers so that when used with 
     * static imports allows more readable code such as:
     * {@code .dataFetcher(async(fooDataFetcher))}
     * <br><br>
     * By default this will run in the {@link ForkJoinPool#commonPool()}. You can set 
     * your own {@link Executor} with {@link #executeIn(Executor)}
     *
     * @param wrappedDataFetcher the data fetcher to run asynchronously
     *
     * @return a DataFetcher that will run the wrappedDataFetcher asynchronously
     */
    public static <T> AsynchronousDataFetcher<T> async(DataFetcher<T> wrappedDataFetcher) {
        return new AsynchronousDataFetcher<>(wrappedDataFetcher);
    }
    
    private final DataFetcher<T> wrappedDataFetcher;
    private Executor executor = ForkJoinPool.commonPool();

    public AsynchronousDataFetcher(DataFetcher<T> wrappedDataFetcher) {
        assertNotNull(wrappedDataFetcher, "wrappedDataFetcher can't be null");
        this.wrappedDataFetcher = wrappedDataFetcher;
    }
    
    /**
     * This allows you to set the {@link Executor} that this {@link DataFetcher}
     * will run in
     * 
     * @param executor the {@link Executor} to run the asynchronous data fetcher in
     * 
     * @return a reference to this object
     */
    public AsynchronousDataFetcher<T> executeIn(Executor executor) {
        this.executor = assertNotNull(executor, "executor can't be null");
        return this;
    }

    @Override
    public CompletableFuture<T> get(DataFetchingEnvironment environment) {
        return CompletableFuture.supplyAsync(() -> wrappedDataFetcher.get(environment), executor);
    }

}
