package graphql.schema;

import graphql.PublicApi;

/**
 * A helper for {@link graphql.schema.DataFetcherFactory}
 */
@PublicApi
public class DataFetcherFactories {

    /**
     * Creates a {@link graphql.schema.DataFetcherFactory} that always returns the provided {@link graphql.schema.DataFetcher}
     *
     * @param dataFetcher the data fetcher to always return
     * @param <T>         the type of the data fetcher
     *
     * @return a data fetcher factory that always returns the provided data fetcher
     */
    public static <T> DataFetcherFactory<T> useDataFetcher(DataFetcher<T> dataFetcher) {
        return fieldDefinition -> dataFetcher;
    }
}
