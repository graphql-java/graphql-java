package graphql.schema;


import java.lang.reflect.Field;
import java.util.Map;

/**
 * Fetches data directly from a field.
 */
public class FieldDataFetcher<T> implements DataFetcher<T> {

    /**
     * The name of the field.
     */
    private final String fieldName;

    /**
     * Ctor.
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
        return (T) getFieldValue(source, environment.getFieldType());
    }

    /**
     * Uses introspection to get the field value.
     *
     * @param object     The object being acted on.
     * @param outputType The output type; ignored in this case.
     * @return An object, or null.
     */
    private Object getFieldValue(Object object, GraphQLOutputType outputType) {
        try {
            Field field = object.getClass().getField(fieldName);
            return field.get(object);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
