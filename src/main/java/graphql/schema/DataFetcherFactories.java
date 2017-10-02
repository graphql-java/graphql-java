package graphql.schema;

import graphql.PublicSpi;

/**
 * A DataFetcherFactory allows a level of indirection in providing {@link graphql.schema.DataFetcher}s for graphql fields.
 *
 * For example if you are using an IoC container such as Spring or Guice, you can use this indirection to give you
 * per request late binding of a data fetcher with its dependencies injected in.
 *
 * @param <T> the type of DataFetcher
 */
@PublicSpi
public interface DataFetcherFactory<T> {

    /**
     * Returns a {@link graphql.schema.DataFetcher}
     *
     * @param fieldDefinition the {@link graphql.schema.GraphQLFieldDefinition} that needs the data fetcher
     *
     * @return a data fetcher
     */
    DataFetcher<T> get(GraphQLFieldDefinition fieldDefinition);

    /**
     * Creates a {@link graphql.schema.DataFetcherFactory} that always returns the provided {@link graphql.schema.DataFetcher}
     *
     * @param dataFetcher the data fetcher to always return
     * @param <DF>        the type of the data fetcher
     *
     * @return a data fetcher factory that always returns the provided data fetcher
     */
    static <DF> DataFetcherFactory<DF> useDataFetcher(DataFetcher<DF> dataFetcher) {
        return fieldDefinition -> dataFetcher;
    }
}
