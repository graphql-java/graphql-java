package graphql.schema;


import graphql.Assert;
import graphql.PublicApi;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This is the default data fetcher used in graphql-java, and it will examine
 * maps, records and POJO java beans for values that match the desired name, typically the field name,
 * or it will use a provided function to obtain values.
 * <p>
 * It uses the following strategies
 * <ul>
 * <li>If the source is null, return null</li>
 * <li>If the source is a Map, return map.get(propertyName)</li>
 * <li>If a function is provided, it is used</li>
 * <li>Find a public JavaBean getter method named `getPropertyName()` or `isPropertyName()`</li>
 * <li>Find any getter method named `getPropertyName()` or `isPropertyName()` and call method.setAccessible(true)</li>
 * <li>Find a public field named `propertyName`</li>
 * <li>Find any field named `propertyName` and call field.setAccessible(true)</li>
 * <li>Find a public Record like method named `propertyName()`</li>
 * <li>If this cant find anything, then null is returned</li>
 * </ul>
 * <p>
 * You can write your own data fetchers to get data from some other backing system
 * if you need highly customised behaviour.
 *
 * @see graphql.schema.DataFetcher
 */
@PublicApi
public class PropertyDataFetcher<T> implements LightDataFetcher<T> {

    private static final PropertyDataFetcher<Object> SINGLETON_FETCHER = new PropertyDataFetcher<>() {
        @Override
        Object fetchImpl(String propertyName, Object source, GraphQLFieldDefinition fieldDefinition, Supplier<DataFetchingEnvironment> environmentSupplier) {
            return super.fetchImpl(fieldDefinition.getName(), source, fieldDefinition, environmentSupplier);
        }
    };

    private static final DataFetcherFactory<?> SINGLETON_FETCHER_FACTORY = environment -> SINGLETON_FETCHER;

    /**
     * This returns the same singleton {@link PropertyDataFetcher} that fetches property values
     * based on the name of the field that iis passed into it.
     *
     * @return a singleton property data fetcher
     */
    public static PropertyDataFetcher<?> singleton() {
        return SINGLETON_FETCHER;
    }

    /**
     * This returns the same singleton {@link DataFetcherFactory} that returns the value of {@link #singleton()}
     *
     * @return a singleton data fetcher factory
     */
    public static DataFetcherFactory<?> singletonFactory() {
        return SINGLETON_FETCHER_FACTORY;
    }


    private final String propertyName;
    private final Function<Object, Object> function;

    /**
     * This constructor will use the property name and examine the {@link DataFetchingEnvironment#getSource()}
     * object for a getter method or field with that name.
     *
     * @param propertyName the name of the property to retrieve
     */
    public PropertyDataFetcher(String propertyName) {
        this.propertyName = Assert.assertNotNull(propertyName);
        this.function = null;
    }

    @SuppressWarnings("unchecked")
    private <O> PropertyDataFetcher(Function<O, T> function) {
        this.function = (Function<Object, Object>) Assert.assertNotNull(function);
        this.propertyName = null;
    }

    private PropertyDataFetcher() {
        this.function = null;
        this.propertyName = null;
    }

    /**
     * Returns a data fetcher that will use the property name to examine the {@link DataFetchingEnvironment#getSource()} object
     * for a getter method or field with that name, or if it's a map, it will look up a value using
     * property name as a key.
     * <p>
     * For example :
     * <pre>
     * {@code
     *
     *      DataFetcher functionDataFetcher = fetching("pojoPropertyName");
     *
     * }
     * </pre>
     *
     * @param propertyName the name of the property to retrieve
     * @param <T>          the type of result
     *
     * @return a new PropertyDataFetcher using the provided function as its source of values
     */
    public static <T> PropertyDataFetcher<T> fetching(String propertyName) {
        return new PropertyDataFetcher<>(propertyName);
    }

    /**
     * Returns a data fetcher that will present the {@link DataFetchingEnvironment#getSource()} object to the supplied
     * function to obtain a value, which allows you to use Java 8 method references say obtain values in a
     * more type safe way.
     * <p>
     * For example :
     * <pre>
     * {@code
     *
     *      DataFetcher functionDataFetcher = fetching(Thing::getId);
     *
     * }
     * </pre>
     *
     * @param function the function to use to obtain a value from the source object
     * @param <O>      the type of the source object
     * @param <T>      the type of result
     *
     * @return a new PropertyDataFetcher using the provided function as its source of values
     */
    public static <T, O> PropertyDataFetcher<T> fetching(Function<O, T> function) {
        return new PropertyDataFetcher<>(function);
    }

    /**
     * @return the property that this is fetching for
     */
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public T get(GraphQLFieldDefinition fieldDefinition, Object source, Supplier<DataFetchingEnvironment> environmentSupplier) throws Exception {
        return fetchImpl(propertyName, source, fieldDefinition, environmentSupplier);
    }

    @Override
    public T get(DataFetchingEnvironment environment) {
        return fetchImpl(propertyName, environment.getSource(), environment.getFieldDefinition(), () -> environment);
    }

    /**
     * This is our implementation of property fetching
     *
     * @param propertyName        the name of the property to fetch in the source object
     * @param source              the source object to fetch from
     * @param fieldDefinition     the field definition of the field being fetched for
     * @param environmentSupplier a supplied of thee {@link DataFetchingEnvironment}
     *
     * @return a value of type T
     */
    @SuppressWarnings("unchecked")
    T fetchImpl(String propertyName, Object source, GraphQLFieldDefinition fieldDefinition, Supplier<DataFetchingEnvironment> environmentSupplier) {
        if (source == null) {
            return null;
        }

        if (function != null) {
            return (T) function.apply(source);
        }

        return (T) PropertyDataFetcherHelper.getPropertyValue(propertyName, source, fieldDefinition.getType(), environmentSupplier);
    }

    /**
     * PropertyDataFetcher caches the methods and fields that map from a class to a property for runtime performance reasons
     * as well as negative misses.
     * <p>
     * However during development you might be using an assistance tool like JRebel to allow you to tweak your code base and this
     * caching may interfere with this.  So you can call this method to clear the cache.  A JRebel plugin could
     * be developed to do just that.
     */
    @SuppressWarnings("unused")
    public static void clearReflectionCache() {
        PropertyDataFetcherHelper.clearReflectionCache();
    }

    /**
     * This can be used to control whether PropertyDataFetcher will use {@link java.lang.reflect.Method#setAccessible(boolean)} to gain access to property
     * values.  By default it PropertyDataFetcher WILL use setAccessible.
     *
     * @param flag whether to use setAccessible
     *
     * @return the previous value of the flag
     */
    public static boolean setUseSetAccessible(boolean flag) {
        return PropertyDataFetcherHelper.setUseSetAccessible(flag);
    }

    /**
     * This can be used to control whether PropertyDataFetcher will cache negative lookups for a property for performance reasons.  By default it PropertyDataFetcher WILL cache misses.
     *
     * @param flag whether to cache misses
     *
     * @return the previous value of the flag
     */
    public static boolean setUseNegativeCache(boolean flag) {
        return PropertyDataFetcherHelper.setUseNegativeCache(flag);
    }
}
