package graphql.schema;

import java.util.Map;

public class DefaultDataFetcher implements DataFetcher {

    private final MapDataFetcher mapFetcher;
    private final PropertyDataFetcher propertyFetcher;
    private final FieldDataFetcher fieldFetcher;

    public DefaultDataFetcher(MapDataFetcher mapFetcher, PropertyDataFetcher propertyFetcher, FieldDataFetcher fieldFetcher) {
        this.mapFetcher = mapFetcher;
        this.propertyFetcher = propertyFetcher;
        this.fieldFetcher = fieldFetcher;
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        Object source = env.getSource();
        if (source == null) return null;
        if (source instanceof Map) {
            return mapFetcher.getValue(source);
        }
        try {
            return propertyFetcher.getProperty(source);
        }
        catch (NoSuchMethodException e) {
            return fieldFetcher.getField(source);
        }
    }
}
