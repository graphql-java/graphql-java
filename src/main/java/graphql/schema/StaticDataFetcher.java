package graphql.schema;


import java.util.List;
import java.util.Map;

public class StaticDataFetcher implements DataFetcher {


    private final Object value;

    public StaticDataFetcher(Object value) {
        this.value = value;
    }

    @Override
    public Object get(Object source, Map<String,Object> arguments) {
        return value;
    }
}
