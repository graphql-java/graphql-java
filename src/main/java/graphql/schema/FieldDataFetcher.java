package graphql.schema;


import graphql.GraphQLException;
import graphql.PublicApi;

import java.util.Map;

/**
 * Fetches data directly from a named java object field.
 */
@PublicApi
public class FieldDataFetcher<T> implements DataFetcher<T> {

    private final String fieldName;

    /**
     * Constructs a new data fetcher that tries to find values from the name field, using
     * {@link DataFetchingEnvironment#getSource()} as the source object.
     *
     * @param fieldName The name of the field.
     */
    public FieldDataFetcher(String fieldName) {
        this.fieldName = fieldName;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get(DataFetchingEnvironment environment) {
        Object source = environment.getSource();
        if (source == null) return null;
        if (source instanceof Map) {
            return (T) ((Map<?, ?>) source).get(fieldName);
        }
        return (T) getFieldValue(source);
    }

    /**
     * Uses introspection to get the field value.
     *
     * @param object The object being acted on.
     *
     * @return An object, or null.
     */
    private Object getFieldValue(Object object) {
        try {
            return object.getClass().getField(fieldName).get(object);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new GraphQLException(e);
        }
    }
}
