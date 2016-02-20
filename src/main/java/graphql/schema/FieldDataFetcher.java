package graphql.schema;


import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;

/**
 * Fetches data directly from a field.
 *
 * @author Andreas Marek
 * @version v1.3
 */
public class FieldDataFetcher implements DataFetcher {

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

    /** {@inheritDoc} */
    @Override
    public Object get(DataFetchingEnvironment environment) {
        Object source = environment.getSource();
        if (source == null) return null;
        if (source instanceof Map) {
            return ((Map<?, ?>) source).get(fieldName);
        }
        return getFieldValue(source, environment.getFieldType());
    }

    /**
     * Uses introspection to get the field value.
     * @param object The object being acted on.
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
