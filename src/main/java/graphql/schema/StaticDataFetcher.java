package graphql.schema;


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
