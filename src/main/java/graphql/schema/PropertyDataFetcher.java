package graphql.schema;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;

public class PropertyDataFetcher implements DataFetcher {

    private final String propertyName;

    public PropertyDataFetcher(String propertyName) {
        this.propertyName = propertyName;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) {
        Object source = environment.getSource(Object.class);
        if (source == null) return null;
        if (source instanceof Map) {
            return ((Map<?, ?>) source).get(propertyName);
        }
        return getPropertyViaGetter(source, environment.getFieldType());
    }

    private Object getPropertyViaGetter(Object object, GraphQLOutputType outputType) {
        try {
            if (isBooleanProperty(outputType)) {
                try {
                    return getPropertyViaGetterUsingPrefix(object, "is");
                } catch (NoSuchMethodException e) {
                    return getPropertyViaGetterUsingPrefix(object, "get");
                }
            } else {
                return getPropertyViaGetterUsingPrefix(object, "get");
            }
        } catch (NoSuchMethodException e1) {
            return getPropertyViaFieldAccess(object);
        }
    }

    private Object getPropertyViaGetterUsingPrefix(Object object, String prefix) throws NoSuchMethodException {
        String getterName = prefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        try {
            Method method = object.getClass().getMethod(getterName);
            return method.invoke(object);

        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
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

    private Object getPropertyViaFieldAccess(Object object) {
        try {
            Field field = object.getClass().getField(propertyName);
            return field.get(object);
        } catch (NoSuchFieldException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
