package graphql.schema;


import graphql.Assert;
import graphql.GraphQLException;
import graphql.PublicApi;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

/**
 * This is the default data fetcher used in graphql-java.  It will examine
 * maps and POJO java beans for values that match the desired name, typically the field name
 * or it will use a provided function to obtain values.
 * maps and POJO java beans for values that match the desired name.
 *
 * It uses the following strategies
 * <ul>
 * <li>If the source is null, return null</li>
 * <li>If the source is a Map, return map.get(propertyName)</li>
 * <li>If a function is provided, it is used</li>
 * <li>Find a public JavaBean getter method named `propertyName`</li>
 * <li>Find any getter method named `propertyName` and call method.setAccessible(true)</li>
 * <li>Find a public field named `propertyName`</li>
 * <li>Find any field named `propertyName` and call field.setAccessible(true)</li>
 * <li>If this cant find anything, then null is returned</li>
 * </ul>
 *
 * You can write your own data fetchers to get data from some other backing system
 * if you need highly customised behaviour.
 *
 * @see graphql.schema.DataFetcher
 */
@PublicApi
public class PropertyDataFetcher<T> implements DataFetcher<T> {

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

    /**
     * Returns a data fetcher that will use the property name to examine the {@link DataFetchingEnvironment#getSource()} object
     * for a getter method or field with that name, or if its a map, it will look up a value using
     * property name as a key.
     *
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
     *
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

    @SuppressWarnings("unchecked")
    @Override
    public T get(DataFetchingEnvironment environment) {
        Object source = environment.getSource();
        if (source == null) {
            return null;
        }

        if (function != null) {
            return (T) function.apply(source);
        }

        if (source instanceof Map) {
            return (T) ((Map<?, ?>) source).get(propertyName);
        }
        return (T) getPropertyViaGetter(source, environment.getFieldType());
    }

    private Object getPropertyViaGetter(Object object, GraphQLOutputType outputType) {
        try {
            return getPropertyViaGetterMethod(object, outputType, this::findPubliclyAccessibleMethod);
        } catch (NoSuchMethodException ignored) {
            try {
                return getPropertyViaGetterMethod(object, outputType, this::findViaSetAccessible);
            } catch (NoSuchMethodException ignored2) {
                return getPropertyViaFieldAccess(object);
            }
        }
    }

    @FunctionalInterface
    private interface MethodFinder {
        Method apply(Class aClass, String s) throws NoSuchMethodException;
    }

    private Object getPropertyViaGetterMethod(Object object, GraphQLOutputType outputType, MethodFinder methodFinder) throws NoSuchMethodException {
        if (isBooleanProperty(outputType)) {
            try {
                return getPropertyViaGetterUsingPrefix(object, "is", methodFinder);
            } catch (NoSuchMethodException e) {
                return getPropertyViaGetterUsingPrefix(object, "get", methodFinder);
            }
        } else {
            return getPropertyViaGetterUsingPrefix(object, "get", methodFinder);
        }
    }

    private Object getPropertyViaGetterUsingPrefix(Object object, String prefix, MethodFinder methodFinder) throws NoSuchMethodException {
        String getterName = prefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        try {
            Method method = methodFinder.apply(object.getClass(), getterName);
            return method.invoke(object);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new GraphQLException(e);
        }
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean isBooleanProperty(GraphQLOutputType outputType) {
        if (outputType == GraphQLBoolean) {
            return true;
        }
        if (isNonNull(outputType)) {
            return unwrapOne(outputType) == GraphQLBoolean;
        }
        return false;
    }

    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * PropertyDataFetcher caches the methods and fields that map from a class to a property for runtime performance reasons.
     *
     * However during development you might be using an assistance tool like JRebel to allow you to tweak your code base and this
     * caching may interfere with this.  So you can call this method to clear the cache.  A JRebel plugin could
     * be developed to do just that.
     */
    @SuppressWarnings("unused")
    public static void clearReflectionCache() {
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
    }

    private String mkKey(Class clazz, String propertyName) {
        return clazz.getName() + "__" + propertyName;
    }

    // by not filling out the stack trace, we gain speed when using the exception as flow control
    private static class FastNoSuchMethodException extends NoSuchMethodException {
        public FastNoSuchMethodException(String methodName) {
            super(methodName);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Invoking public methods on package-protected classes via reflection
     * causes exceptions. This method searches a class's hierarchy for
     * public visibility parent classes with the desired getter. This
     * particular case is required to support AutoValue style data classes,
     * which have abstract public interfaces implemented by package-protected
     * (generated) subclasses.
     */
    @SuppressWarnings("unchecked")
    private Method findPubliclyAccessibleMethod(Class root, String methodName) throws NoSuchMethodException {
        Class currentClass = root;
        while (currentClass != null) {
            String key = mkKey(currentClass, propertyName);
            Method method = METHOD_CACHE.get(key);
            if (method != null) {
                return method;
            }
            if (Modifier.isPublic(currentClass.getModifiers())) {
                method = currentClass.getMethod(methodName);
                if (Modifier.isPublic(method.getModifiers())) {
                    METHOD_CACHE.putIfAbsent(key, method);
                    return method;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return root.getMethod(methodName);
    }

    private Method findViaSetAccessible(Class aClass, String methodName) throws NoSuchMethodException {
        String key = mkKey(aClass, propertyName);
        Method method = METHOD_CACHE.get(key);
        if (method != null) {
            return method;
        }

        Method[] declaredMethods = aClass.getDeclaredMethods();
        Optional<Method> m = Arrays.stream(declaredMethods)
                .filter(mth -> methodName.equals(mth.getName()))
                .findFirst();
        if (m.isPresent()) {
            try {
                // few JVMs actually enforce this but it might happen
                method = m.get();
                method.setAccessible(true);
                METHOD_CACHE.putIfAbsent(key, method);
                return method;
            } catch (SecurityException ignored) {
            }
        }
        throw new FastNoSuchMethodException(methodName);
    }

    private Object getPropertyViaFieldAccess(Object object) {
        Class<?> aClass = object.getClass();
        String key = mkKey(aClass, propertyName);
        try {
            Field field = FIELD_CACHE.get(key);
            if (field == null) {
                field = aClass.getField(propertyName);
                FIELD_CACHE.putIfAbsent(key, field);
            }
            return field.get(object);
        } catch (NoSuchFieldException e) {
            // if not public fields then try via setAccessible
            try {
                Field field = aClass.getDeclaredField(propertyName);
                field.setAccessible(true);
                FIELD_CACHE.putIfAbsent(key, field);
                return field.get(object);
            } catch (SecurityException | NoSuchFieldException ignored2) {
                return null;
            } catch (IllegalAccessException e1) {
                throw new GraphQLException(e);
            }
        } catch (IllegalAccessException e) {
            throw new GraphQLException(e);
        }
    }
}
