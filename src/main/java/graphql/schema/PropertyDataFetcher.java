package graphql.schema;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static graphql.Assert.assertNotNull;
import static graphql.Scalars.GraphQLBoolean;

public class PropertyDataFetcher implements DataFetcher {

    private final String propertyName;
    private final boolean isBoolean;

    public PropertyDataFetcher(String propertyName, GraphQLOutputType type) {
        assertNotNull(propertyName, "`propertyName` can't be null");
        assertNotNull(type, "`type` can't be null");
        this.propertyName = propertyName;
        this.isBoolean = isBooleanProperty(type);
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        Object source = env.getSource();
        if (source == null) return null;
        try {
            return getProperty(source);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    Object getProperty(Object source) throws NoSuchMethodException {
        return new PropertyGetter(source).getProperty();
    }

    private boolean isBooleanProperty(GraphQLType type) {
        if (type instanceof GraphQLModifiedType) return isBooleanProperty(((GraphQLModifiedType) type).getWrappedType());
        return type == GraphQLBoolean;
    }

    private class PropertyGetter {

        private final Object source;

        PropertyGetter(Object source) {
            this.source = source;
        }

        Object getProperty() throws NoSuchMethodException {
            if (isBoolean) {
                try {
                    return getPropertyByPrefix("is");
                } catch (NoSuchMethodException e) {
                    return getPropertyByPrefix("get");
                }
            } else {
                return getPropertyByPrefix("get");
            }
        }

        private Object getPropertyByPrefix(String prefix) throws NoSuchMethodException {
            String getterName = prefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            try {
                Method method = source.getClass().getMethod(getterName);
                return method.invoke(source);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
