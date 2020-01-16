package graphql.schema;

import graphql.GraphQLException;
import graphql.Internal;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

/**
 * This class is the guts of a property data fetcher and also used in AST code to turn
 * in memory java objects into AST elements
 */
@Internal
public class PropertyDataFetcherHelper {
    private static final AtomicBoolean USE_SET_ACCESSIBLE = new AtomicBoolean(true);
    private static final AtomicBoolean USE_NEGATIVE_CACHE = new AtomicBoolean(true);
    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> NEGATIVE_CACHE = new ConcurrentHashMap<>();

    public static Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType) {
        return getPropertyValue(propertyName, object, graphQLType, null);
    }

    public static Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType, DataFetchingEnvironment environment) {
        if (object == null) {
            return null;
        }
        if (object instanceof Map) {
            return ((Map<?, ?>) object).get(propertyName);
        }

        String key = mkKey(object, propertyName);
        if (isNegativelyCached(key)) {
            return null;
        }
        boolean dfeInUse = environment != null;
        try {
            return getPropertyViaGetterMethod(object, propertyName, graphQLType, (root, methodName) -> findPubliclyAccessibleMethod(propertyName, root, methodName, dfeInUse), environment);
        } catch (NoSuchMethodException ignored) {
            try {
                return getPropertyViaGetterMethod(object, propertyName, graphQLType, (aClass, methodName) -> findViaSetAccessible(propertyName, aClass, methodName, dfeInUse), environment);
            } catch (NoSuchMethodException ignored2) {
                try {
                    return getPropertyViaFieldAccess(object, propertyName);
                } catch (FastNoSuchMethodException e) {
                    // we have nothing to ask for and we have exhausted our lookup strategies
                    putInNegativeCache(key);
                    return null;
                }
            }
        }
    }

    private static boolean isNegativelyCached(String key) {
        if (USE_NEGATIVE_CACHE.get()) {
            return NEGATIVE_CACHE.containsKey(key);
        }
        return false;
    }

    private static void putInNegativeCache(String key) {
        if (USE_NEGATIVE_CACHE.get()) {
            NEGATIVE_CACHE.put(key, key);
        }
    }

    private interface MethodFinder {
        Method apply(Class<?> aClass, String s) throws NoSuchMethodException;
    }

    private static Object getPropertyViaGetterMethod(Object object, String propertyName, GraphQLType graphQLType, MethodFinder methodFinder, DataFetchingEnvironment environment) throws NoSuchMethodException {
        if (isBooleanProperty(graphQLType)) {
            try {
                return getPropertyViaGetterUsingPrefix(object, propertyName, "is", methodFinder, environment);
            } catch (NoSuchMethodException e) {
                return getPropertyViaGetterUsingPrefix(object, propertyName, "get", methodFinder, environment);
            }
        } else {
            return getPropertyViaGetterUsingPrefix(object, propertyName, "get", methodFinder, environment);
        }
    }

    private static Object getPropertyViaGetterUsingPrefix(Object object, String propertyName, String prefix, MethodFinder methodFinder, DataFetchingEnvironment environment) throws NoSuchMethodException {
        String getterName = prefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        try {
            Method method = methodFinder.apply(object.getClass(), getterName);
            if (takesDataFetcherEnvironmentAsOnlyArgument(method)) {
                if (environment == null) {
                    throw new FastNoSuchMethodException(getterName);
                }
                return method.invoke(object, environment);
            } else {
                return method.invoke(object);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new GraphQLException(e);
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
    private static Method findPubliclyAccessibleMethod(String propertyName, Class<?> root, String methodName, boolean dfeInUse) throws NoSuchMethodException {
        Class<?> currentClass = root;
        while (currentClass != null) {
            String key = mkKey(currentClass, propertyName);
            Method method = METHOD_CACHE.get(key);
            if (method != null) {
                return method;
            }
            if (Modifier.isPublic(currentClass.getModifiers())) {
                if (dfeInUse) {
                    //
                    // try a getter that takes DataFetchingEnvironment first (if we have one)
                    try {
                        method = currentClass.getMethod(methodName, DataFetchingEnvironment.class);
                        if (Modifier.isPublic(method.getModifiers())) {
                            METHOD_CACHE.putIfAbsent(key, method);
                            return method;
                        }
                    } catch (NoSuchMethodException e) {
                        // ok try the next approach
                    }
                }
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

    private static Method findViaSetAccessible(String propertyName, Class<?> aClass, String methodName, boolean dfeInUse) throws NoSuchMethodException {
        if (!USE_SET_ACCESSIBLE.get()) {
            throw new FastNoSuchMethodException(methodName);
        }
        Class<?> currentClass = aClass;
        while (currentClass != null) {
            String key = mkKey(currentClass, propertyName);
            Method method = METHOD_CACHE.get(key);
            if (method != null) {
                return method;
            }

            Predicate<Method> whichMethods = mth -> {
                if (dfeInUse) {
                    return hasZeroArgs(mth) || takesDataFetcherEnvironmentAsOnlyArgument(mth);
                }
                return hasZeroArgs(mth);
            };
            Method[] declaredMethods = currentClass.getDeclaredMethods();
            Optional<Method> m = Arrays.stream(declaredMethods)
                    .filter(mth -> methodName.equals(mth.getName()))
                    .filter(whichMethods)
                    .min(mostMethodArgsFirst());
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
            currentClass = currentClass.getSuperclass();
        }
        throw new FastNoSuchMethodException(methodName);
    }

    private static Object getPropertyViaFieldAccess(Object object, String propertyName) throws FastNoSuchMethodException {
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
            if (!USE_SET_ACCESSIBLE.get()) {
                throw new FastNoSuchMethodException(key);
            }
            // if not public fields then try via setAccessible
            try {
                Field field = aClass.getDeclaredField(propertyName);
                field.setAccessible(true);
                FIELD_CACHE.putIfAbsent(key, field);
                return field.get(object);
            } catch (SecurityException | NoSuchFieldException ignored2) {
                throw new FastNoSuchMethodException(key);
            } catch (IllegalAccessException e1) {
                throw new GraphQLException(e);
            }
        } catch (IllegalAccessException e) {
            throw new GraphQLException(e);
        }
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private static boolean isBooleanProperty(GraphQLType graphQLType) {
        if (graphQLType == GraphQLBoolean) {
            return true;
        }
        if (isNonNull(graphQLType)) {
            return unwrapOne(graphQLType) == GraphQLBoolean;
        }
        return false;
    }

    public static void clearReflectionCache() {
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
        NEGATIVE_CACHE.clear();
    }

    public static boolean setUseSetAccessible(boolean flag) {
        return USE_SET_ACCESSIBLE.getAndSet(flag);
    }

    public static boolean setUseNegativeCache(boolean flag) {
        return USE_NEGATIVE_CACHE.getAndSet(flag);
    }

    private static String mkKey(Object object, String propertyName) {
        return mkKey(object.getClass(), propertyName);
    }

    private static String mkKey(Class<?> clazz, String propertyName) {
        return clazz.getCanonicalName() + "__" + propertyName;
    }

    // by not filling out the stack trace, we gain speed when using the exception as flow control
    private static boolean hasZeroArgs(Method mth) {
        return mth.getParameterCount() == 0;
    }

    private static boolean takesDataFetcherEnvironmentAsOnlyArgument(Method mth) {
        return mth.getParameterCount() == 1 &&
                mth.getParameterTypes()[0].equals(DataFetchingEnvironment.class);
    }

    private static Comparator<? super Method> mostMethodArgsFirst() {
        return Comparator.comparingInt(Method::getParameterCount).reversed();
    }

    @SuppressWarnings("serial")
    private static class FastNoSuchMethodException extends NoSuchMethodException {
        public FastNoSuchMethodException(String methodName) {
            super(methodName);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
