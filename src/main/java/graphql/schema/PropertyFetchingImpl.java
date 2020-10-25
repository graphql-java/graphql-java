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

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

/**
 * A re-usable class that can fetch from POJOs
 */
@Internal
public class PropertyFetchingImpl {

    private final AtomicBoolean USE_SET_ACCESSIBLE = new AtomicBoolean(true);
    private final AtomicBoolean USE_NEGATIVE_CACHE = new AtomicBoolean(true);
    private final ConcurrentMap<String, CachedMethod> METHOD_CACHE = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> NEGATIVE_CACHE = new ConcurrentHashMap<>();
    private final Class<?> singleArgumentType;

    public PropertyFetchingImpl(Class<?> singleArgumentType) {
        this.singleArgumentType = singleArgumentType;
    }

    private class CachedMethod {
        Method method;
        boolean takesSingleArgumentTypeAsOnlyArgument;

        CachedMethod(Method method) {
            this.method = method;
            this.takesSingleArgumentTypeAsOnlyArgument = takesSingleArgumentTypeAsOnlyArgument(method);
        }

    }

    public Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType) {
        return getPropertyValue(propertyName, object, graphQLType, null);
    }

    public Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType, Object singleArgumentValue) {
        if (object instanceof Map) {
            return ((Map<?, ?>) object).get(propertyName);
        }

        String cacheKey = mkKey(object, propertyName);
        // lets try positive cache mechanisms first.  If we have seen the method or field before
        // then we invoke it directly without burning any cycles doing reflection.
        CachedMethod cachedMethod = METHOD_CACHE.get(cacheKey);
        if (cachedMethod != null) {
            try {
                return invokeMethod(object, singleArgumentValue, cachedMethod.method, cachedMethod.takesSingleArgumentTypeAsOnlyArgument);
            } catch (NoSuchMethodException ignored) {
                assertShouldNeverHappen("A method cached as '%s' is no longer available??", cacheKey);
            }
        }
        Field cachedField = FIELD_CACHE.get(cacheKey);
        if (cachedField != null) {
            return invokeField(object, cachedField);
        }

        //
        // if we have tried all strategies before and they have all failed then we negatively cache
        // the cacheKey and assume that its never going to turn up.  This shortcuts the property lookup
        // in systems where there was a `foo` graphql property but they never provided an POJO
        // version of `foo`.
        //
        // we do this second because we believe in the positive cached version will mostly prevail
        // but if we then look it up and negatively cache it then lest do that look up next
        //
        if (isNegativelyCached(cacheKey)) {
            return null;
        }
        //
        // ok we haven't cached it and we haven't negatively cached it so we have to find the POJO method which is the most
        // expensive operation here
        //
        boolean dfeInUse = singleArgumentValue != null;
        try {
            MethodFinder methodFinder = (root, methodName) -> findPubliclyAccessibleMethod(cacheKey, root, methodName, dfeInUse);
            return getPropertyViaGetterMethod(object, propertyName, graphQLType, methodFinder, singleArgumentValue);
        } catch (NoSuchMethodException ignored) {
            try {
                MethodFinder methodFinder = (aClass, methodName) -> findViaSetAccessible(cacheKey, aClass, methodName, dfeInUse);
                return getPropertyViaGetterMethod(object, propertyName, graphQLType, methodFinder, singleArgumentValue);
            } catch (NoSuchMethodException ignored2) {
                try {
                    return getPropertyViaFieldAccess(cacheKey, object, propertyName);
                } catch (FastNoSuchMethodException e) {
                    // we have nothing to ask for and we have exhausted our lookup strategies
                    putInNegativeCache(cacheKey);
                    return null;
                }
            }
        }
    }

    private boolean isNegativelyCached(String key) {
        if (USE_NEGATIVE_CACHE.get()) {
            return NEGATIVE_CACHE.containsKey(key);
        }
        return false;
    }

    private void putInNegativeCache(String key) {
        if (USE_NEGATIVE_CACHE.get()) {
            NEGATIVE_CACHE.put(key, key);
        }
    }

    private interface MethodFinder {
        Method apply(Class<?> aClass, String s) throws NoSuchMethodException;
    }

    private Object getPropertyViaGetterMethod(Object object, String propertyName, GraphQLType graphQLType, MethodFinder methodFinder, Object singleArgumentValue) throws NoSuchMethodException {
        if (isBooleanProperty(graphQLType)) {
            try {
                return getPropertyViaGetterUsingPrefix(object, propertyName, "is", methodFinder, singleArgumentValue);
            } catch (NoSuchMethodException e) {
                return getPropertyViaGetterUsingPrefix(object, propertyName, "get", methodFinder, singleArgumentValue);
            }
        } else {
            return getPropertyViaGetterUsingPrefix(object, propertyName, "get", methodFinder, singleArgumentValue);
        }
    }

    private Object getPropertyViaGetterUsingPrefix(Object object, String propertyName, String prefix, MethodFinder methodFinder, Object singleArgumentValue) throws NoSuchMethodException {
        String getterName = prefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        Method method = methodFinder.apply(object.getClass(), getterName);
        return invokeMethod(object, singleArgumentValue, method, takesSingleArgumentTypeAsOnlyArgument(method));
    }

    /**
     * Invoking public methods on package-protected classes via reflection
     * causes exceptions. This method searches a class's hierarchy for
     * public visibility parent classes with the desired getter. This
     * particular case is required to support AutoValue style data classes,
     * which have abstract public interfaces implemented by package-protected
     * (generated) subclasses.
     */
    private Method findPubliclyAccessibleMethod(String cacheKey, Class<?> rootClass, String methodName, boolean dfeInUse) throws NoSuchMethodException {
        Class<?> currentClass = rootClass;
        while (currentClass != null) {
            if (Modifier.isPublic(currentClass.getModifiers())) {
                if (dfeInUse) {
                    //
                    // try a getter that takes singleArgumentType first (if we have one)
                    try {
                        Method method = currentClass.getMethod(methodName, singleArgumentType);
                        if (Modifier.isPublic(method.getModifiers())) {
                            METHOD_CACHE.putIfAbsent(cacheKey, new CachedMethod(method));
                            return method;
                        }
                    } catch (NoSuchMethodException e) {
                        // ok try the next approach
                    }
                }
                Method method = currentClass.getMethod(methodName);
                if (Modifier.isPublic(method.getModifiers())) {
                    METHOD_CACHE.putIfAbsent(cacheKey, new CachedMethod(method));
                    return method;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        assert rootClass != null;
        return rootClass.getMethod(methodName);
    }

    private Method findViaSetAccessible(String cacheKey, Class<?> aClass, String methodName, boolean dfeInUse) throws NoSuchMethodException {
        if (!USE_SET_ACCESSIBLE.get()) {
            throw new FastNoSuchMethodException(methodName);
        }
        Class<?> currentClass = aClass;
        while (currentClass != null) {
            Predicate<Method> whichMethods = mth -> {
                if (dfeInUse) {
                    return hasZeroArgs(mth) || takesSingleArgumentTypeAsOnlyArgument(mth);
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
                    Method method = m.get();
                    method.setAccessible(true);
                    METHOD_CACHE.putIfAbsent(cacheKey, new CachedMethod(method));
                    return method;
                } catch (SecurityException ignored) {
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        throw new FastNoSuchMethodException(methodName);
    }

    private Object getPropertyViaFieldAccess(String cacheKey, Object object, String propertyName) throws FastNoSuchMethodException {
        Class<?> aClass = object.getClass();
        try {
            Field field = aClass.getField(propertyName);
            FIELD_CACHE.putIfAbsent(cacheKey, field);
            return field.get(object);
        } catch (NoSuchFieldException e) {
            if (!USE_SET_ACCESSIBLE.get()) {
                throw new FastNoSuchMethodException(cacheKey);
            }
            // if not public fields then try via setAccessible
            try {
                Field field = aClass.getDeclaredField(propertyName);
                field.setAccessible(true);
                FIELD_CACHE.putIfAbsent(cacheKey, field);
                return field.get(object);
            } catch (SecurityException | NoSuchFieldException ignored2) {
                throw new FastNoSuchMethodException(cacheKey);
            } catch (IllegalAccessException e1) {
                throw new GraphQLException(e);
            }
        } catch (IllegalAccessException e) {
            throw new GraphQLException(e);
        }
    }

    private Object invokeMethod(Object object, Object singleArgumentValue, Method method, boolean takesSingleArgument) throws FastNoSuchMethodException {
        try {
            if (takesSingleArgument) {
                if (singleArgumentValue == null) {
                    throw new FastNoSuchMethodException(method.getName());
                }
                return method.invoke(object, singleArgumentValue);
            } else {
                return method.invoke(object);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new GraphQLException(e);
        }
    }

    private Object invokeField(Object object, Field field) {
        try {
            return field.get(object);
        } catch (IllegalAccessException e) {
            throw new GraphQLException(e);
        }
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean isBooleanProperty(GraphQLType graphQLType) {
        if (graphQLType == GraphQLBoolean) {
            return true;
        }
        if (isNonNull(graphQLType)) {
            return unwrapOne(graphQLType) == GraphQLBoolean;
        }
        return false;
    }

    public void clearReflectionCache() {
        METHOD_CACHE.clear();
        FIELD_CACHE.clear();
        NEGATIVE_CACHE.clear();
    }

    public boolean setUseSetAccessible(boolean flag) {
        return USE_SET_ACCESSIBLE.getAndSet(flag);
    }

    public boolean setUseNegativeCache(boolean flag) {
        return USE_NEGATIVE_CACHE.getAndSet(flag);
    }

    private String mkKey(Object object, String propertyName) {
        Class<?> clazz = object.getClass();
        ClassLoader classLoader = clazz.getClassLoader();
        if (classLoader != null) {
            return classLoader.hashCode() + "__" + clazz.getName() + "__" + propertyName;
        } else {
            return clazz.getName() + "__" + propertyName;
        }
    }

    // by not filling out the stack trace, we gain speed when using the exception as flow control
    private boolean hasZeroArgs(Method mth) {
        return mth.getParameterCount() == 0;
    }

    private boolean takesSingleArgumentTypeAsOnlyArgument(Method mth) {
        return mth.getParameterCount() == 1 &&
                mth.getParameterTypes()[0].equals(singleArgumentType);
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
