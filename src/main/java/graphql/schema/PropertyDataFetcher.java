package graphql.schema;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;

/**
 * <p>PropertyDataFetcher class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class PropertyDataFetcher implements DataFetcher {

    private final String propertyName;

    /**
     * <p>Constructor for PropertyDataFetcher.</p>
     *
     * @param propertyName a {@link java.lang.String} object.
     */
    public PropertyDataFetcher(String propertyName) {
        this.propertyName = propertyName;
    }

    /** {@inheritDoc} */
    @Override
    public Object get(DataFetchingEnvironment environment) {
        Object source = environment.getSource();
        if (source == null) return null;
        if (source instanceof Map) {
            return ((Map<?, ?>) source).get(propertyName);
        }
        return getPropertyViaGetter(source, environment.getFieldType());
    }

    private Object getPropertyViaGetter(Object object, GraphQLOutputType outputType) {
        String prefix = isBooleanProperty(outputType) ? "is" : "get";
        String getterName = prefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        try {
            Method method = object.getClass().getMethod(getterName);
            return method.invoke(object);

        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isBooleanProperty(GraphQLOutputType outputType) {
        if (outputType == GraphQLBoolean) return true;
        if (outputType instanceof GraphQLNonNull) {
            return ((GraphQLNonNull) outputType).getWrappedType() == GraphQLBoolean;
        }
        return false;
    }
}
