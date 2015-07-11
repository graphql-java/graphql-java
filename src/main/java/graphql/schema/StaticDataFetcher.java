package graphql.schema;


import java.util.List;

public class StaticDataFetcher implements DataFetcher {


    private final Object value;

    public StaticDataFetcher(Object value) {
        this.value = value;
    }

    @Override
    public Object get(Object source, List<Object> arguments) {
        return value;
    }
}
