package graphql.schema;


import java.util.List;
import java.util.Map;

public interface DataFetcher {

    Object get(Object source, Map<String,Object> arguments);
}
