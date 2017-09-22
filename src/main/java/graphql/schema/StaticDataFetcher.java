package graphql.schema;


import graphql.PublicApi;

/**
 * A {@link graphql.schema.DataFetcher} that always returns the same value
 */
@PublicApi
public class StaticDataFetcher implements DataFetcher {


    private final Object value;

    public StaticDataFetcher(Object value) {
        this.value = value;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        return value;
    }
}
