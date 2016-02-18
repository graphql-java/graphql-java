package graphql.schema;

import java.lang.reflect.Field;

/**
 * Fetches data directly from a field.
 */
public class FieldDataFetcher
    extends AbstractReflectionDataFetcher
    implements DataFetcher {

    /**
     * Ctor.
     * @param fieldName The name of the field.
     */
    public FieldDataFetcher(
        String fieldName) {

        super(fieldName);
    }

    /**
     * Uses introspection to get the field value.
     * @param object The object being acted on.
     * @param outputType The output type; ignored in this case.
     * @return An object, or null.
     */
    protected Object getValue(
        Object target,
        GraphQLOutputType outputType) {

        try {
            Field field = target.getClass().getField(name);
            return field.get(target);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
