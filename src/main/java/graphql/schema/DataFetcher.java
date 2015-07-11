package graphql.schema;


import java.util.List;

public interface DataFetcher {

    Object get(Object source, List<Object> arguments);
}
