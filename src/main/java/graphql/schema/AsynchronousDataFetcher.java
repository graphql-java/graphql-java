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
     * <p>
     * {@code .dataFetcher(async(fooDataFetcher))}
     * <p>
     * By default this will run in the {@link ForkJoinPool#commonPool()}. You can set 
     * your own {@link Executor} with {@link #asyncWithExecutor(DataFetcher, Executor)}
     *
     * @param wrappedDataFetcher the data fetcher to run asynchronously
     *
     * @return a {@link DataFetcher} that will run the wrappedDataFetcher asynchronously
     */
    public static <T> AsynchronousDataFetcher<T> async(DataFetcher<T> wrappedDataFetcher) {
        return new AsynchronousDataFetcher<>(wrappedDataFetcher);
    }
    
    /**
     * A factory method for creating asynchronous data fetchers and setting the 
     * {@link Executor} they run in so that when used with static imports allows 
     * more readable code such as:
     * <p>
     * {@code .dataFetcher(asyncWithExecutor(fooDataFetcher, fooPool))}
     *
     * @param wrappedDataFetcher the data fetcher to run asynchronously
     * @param executor to run the asynchronous data fetcher in
     *
     * @return a {@link DataFetcher} that will run the wrappedDataFetcher asynchronously in 
     * the given {@link Executor}
     */
    public static <T> AsynchronousDataFetcher<T> asyncWithExecutor(DataFetcher<T> wrappedDataFetcher,
            Executor executor) {
        return new AsynchronousDataFetcher<>(wrappedDataFetcher, executor);
    }
    
    private final DataFetcher<T> wrappedDataFetcher;
    private final Executor executor;

    public AsynchronousDataFetcher(DataFetcher<T> wrappedDataFetcher) {
        this(wrappedDataFetcher, ForkJoinPool.commonPool());
    }

    public AsynchronousDataFetcher(DataFetcher<T> wrappedDataFetcher, Executor executor) {
        this.wrappedDataFetcher = assertNotNull(wrappedDataFetcher, "wrappedDataFetcher can't be null");
        this.executor = assertNotNull(executor, "executor can't be null");
    }

    @Override
    public CompletableFuture<T> get(DataFetchingEnvironment environment) {
        return CompletableFuture.supplyAsync(() -> wrappedDataFetcher.get(environment), executor);
    }

}
