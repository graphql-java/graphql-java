package graphql.schema;

import static graphql.Assert.assertNotNull;

import java.util.concurrent.CompletableFuture;

import graphql.PublicApi;

/**
 * A modified type for that indicates the underlying data fetcher is run asynchronously.
 */
@PublicApi
public class AsynchronousDataFetcher<T> implements DataFetcher<CompletableFuture<T>> {
    
    /**
     * A factory method for creating asynchronous data fetchers so that when used with 
     * static imports allows more readable code such as
     * {@code .dataFetcher(async(fooDataFetcher)) }
     *
     * @param wrappedDataFetcher the data fetcher to run asynchronously
     *
     * @return a DataFetcher that will run the wrappedDataFetcher asynchronously
     */
    public static <T> AsynchronousDataFetcher<T> async(DataFetcher<T> wrappedDataFetcher) {
        return new AsynchronousDataFetcher<>(wrappedDataFetcher);
    }
    
    private final DataFetcher<T> wrappedDataFetcher;

    public AsynchronousDataFetcher(DataFetcher<T> wrappedDataFetcher) {
        assertNotNull(wrappedDataFetcher, "wrappedDataFetcher can't be null");
        this.wrappedDataFetcher = wrappedDataFetcher;
    }

    @Override
    public CompletableFuture<T> get(DataFetchingEnvironment environment) {
        return CompletableFuture.supplyAsync(() -> wrappedDataFetcher.get(environment));
    }

}
