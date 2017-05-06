package graphql.schema;


import graphql.PublicSpi;

@PublicSpi
public interface DataFetcher<T> {

    T get(DataFetchingEnvironment environment);
}
