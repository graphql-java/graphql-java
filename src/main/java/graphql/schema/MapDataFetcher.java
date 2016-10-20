package graphql.schema;

import java.util.Map;

import static graphql.Assert.assertNotNull;

public class MapDataFetcher implements DataFetcher {

    private final String keyName;

    public MapDataFetcher(String keyName) {
        assertNotNull(keyName, "`keyName` can't be null");
        this.keyName = keyName;
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        Object source = env.getSource();
        if (source == null || !(source instanceof Map)) return null;
        return getValue(source);
    }

    Object getValue(Object source) {
        return ((Map<String, ?>) source).get(keyName);
    }
}
