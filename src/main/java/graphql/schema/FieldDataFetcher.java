package graphql.schema;


import java.lang.reflect.Field;

import static graphql.Assert.assertNotNull;

/**
 * Fetches data directly from a field.
 */
public class FieldDataFetcher implements DataFetcher {

    private final String fieldName;

    public FieldDataFetcher(String fieldName) {
        assertNotNull(fieldName, "`fieldName` can't be null");
        this.fieldName = fieldName;
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        Object source = env.getSource();
        if (source == null) return null;
        return getField(source);
    }

    Object getField(Object source) {
        Field field;
        try {
            field = source.getClass().getField(fieldName);
        } catch (NoSuchFieldException e) {
            return null;
        }
        try {
            return field.get(source);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
