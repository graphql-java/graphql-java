package graphql.schema;


public interface DataFetcher {

    Object get(DataFetchingEnvironment environment);
}
