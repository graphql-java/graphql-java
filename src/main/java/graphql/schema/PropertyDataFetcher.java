package graphql.schema;


import org.apache.commons.beanutils.PropertyUtils;

import java.lang.reflect.InvocationTargetException;

public class PropertyDataFetcher<T> implements DataFetcher<T> {

    private final String propertyName;

    public PropertyDataFetcher(String propertyName) {
        this.propertyName = propertyName;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get(DataFetchingEnvironment environment) {
        Object source = environment.getSource();
        if (source == null) return null;
        try {
            return (T) PropertyUtils.getProperty(source, propertyName);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

}
