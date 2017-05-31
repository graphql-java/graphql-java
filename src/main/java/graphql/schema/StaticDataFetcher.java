package graphql.schema;


public class StaticDataFetcher<T> implements DataFetcher<T> {


    private final T value;

    public StaticDataFetcher(T value) {
        this.value = value;
    }

    @Override
    public T get(DataFetchingEnvironment environment) {
        return value;
    }
}
