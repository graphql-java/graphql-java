package graphql.schema;


public interface DataFetcher<T> {

    T get(DataFetchingEnvironment environment);
}
