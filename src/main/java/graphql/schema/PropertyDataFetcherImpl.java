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


@Internal
public class PropertyDataFetcherImpl {

    private final AtomicBoolean USE_SET_ACCESSIBLE = new AtomicBoolean(true);
    private final AtomicBoolean USE_NEGATIVE_CACHE = new AtomicBoolean(true);
    private final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> NEGATIVE_CACHE = new ConcurrentHashMap<>();

    private final Class<?> singleArgumentType;

    /**
     * @param singleArgumentType This is the type of the single argument a getter can take.
     *                           If null no special getter are considered.
     */
    public PropertyDataFetcherImpl(Class<?> singleArgumentType) {
        this.singleArgumentType = singleArgumentType;
    }


    public Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType) {
        return getPropertyValue(propertyName, object, graphQLType, null);
    }

    public Object getPropertyValue(String propertyName, Object object, GraphQLType graphQLType, Object singleArgumentValue) {
        if (object instanceof Map) {
            return ((Map<?, ?>) object).get(propertyName);
        }

        String key = mkKey(object, propertyName);
        //
        // if we have tried all strategies before and they have all failed then we negatively cache
        // the key and assume that its never going to turn up.  This shortcuts the property lookup
        // in systems where there was a `foo` graphql property but they never provided an POJO
        // version of `foo`.
        if (isNegativelyCached(key)) {
            return null;
        }
        // lets try positive cache mechanisms next.  If we have seen the method or field before
        // then we invoke it directly without burning any cycles doing reflection.
        Method cachedMethod = METHOD_CACHE.get(key);
        if (cachedMethod != null) {
            MethodFinder methodFinder = (aClass, methodName) -> cachedMethod;
            try {
                return getPropertyViaGetterMethod(object, propertyName, graphQLType, methodFinder, singleArgumentValue);
            } catch (NoSuchMethodException ignored) {
                assertShouldNeverHappen("A method cached as '%s' is no longer available??", key);
            }
        }
        Field cachedField = FIELD_CACHE.get(key);
        if (cachedField != null) {
            try {
                return getPropertyViaFieldAccess(object, propertyName);
            } catch (FastNoSuchMethodException ignored) {
                assertShouldNeverHappen("A field cached as '%s' is no longer available??", key);
            }
        }

        boolean singlArgumentValueProvided = singleArgumentValue != null;
        try {
            MethodFinder methodFinder = (root, methodName) -> findPubliclyAccessibleMethod(propertyName, root, methodName, singlArgumentValueProvided);
            return getPropertyViaGetterMethod(object, propertyName, graphQLType, methodFinder, singleArgumentValue);
        } catch (NoSuchMethodException ignored) {
            try {
                MethodFinder methodFinder = (aClass, methodName) -> findViaSetAccessible(propertyName, aClass, methodName, singlArgumentValueProvided);
                return getPropertyViaGetterMethod(object, propertyName, graphQLType, methodFinder, singleArgumentValue);
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
        try {
            Method method = methodFinder.apply(object.getClass(), getterName);
            if (takesSingleArgumentOnly(method)) {
                if (singleArgumentValue == null) {
                    throw new FastNoSuchMethodException(getterName);
                }
                return method.invoke(object, singleArgumentValue);
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
    private Method findPubliclyAccessibleMethod(String propertyName, Class<?> root, String methodName, boolean dfeInUse) throws NoSuchMethodException {
        Class<?> currentClass = root;
        while (currentClass != null) {
            String key = mkKey(currentClass, propertyName);
            if (Modifier.isPublic(currentClass.getModifiers())) {
                if (dfeInUse) {
                    //
                    // try a getter that takes singleArgumentType first (if we have one)
                    try {
                        Method method = currentClass.getMethod(methodName, singleArgumentType);
                        if (Modifier.isPublic(method.getModifiers())) {
                            METHOD_CACHE.putIfAbsent(key, method);
                            return method;
                        }
                    } catch (NoSuchMethodException e) {
                        // ok try the next approach
                    }
                }
                Method method = currentClass.getMethod(methodName);
                if (Modifier.isPublic(method.getModifiers())) {
                    METHOD_CACHE.putIfAbsent(key, method);
                    return method;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return root.getMethod(methodName);
    }

    private Method findViaSetAccessible(String propertyName, Class<?> aClass, String methodName, boolean dfeInUse) throws NoSuchMethodException {
        if (!USE_SET_ACCESSIBLE.get()) {
            throw new FastNoSuchMethodException(methodName);
        }
        Class<?> currentClass = aClass;
        while (currentClass != null) {
            String key = mkKey(currentClass, propertyName);

            Predicate<Method> whichMethods = mth -> {
                if (dfeInUse) {
                    return hasZeroArgs(mth) || takesSingleArgumentOnly(mth);
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
                    METHOD_CACHE.putIfAbsent(key, method);
                    return method;
                } catch (SecurityException ignored) {
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        throw new FastNoSuchMethodException(methodName);
    }

    private Object getPropertyViaFieldAccess(Object object, String propertyName) throws FastNoSuchMethodException {
        Class<?> aClass = object.getClass();
        String key = mkKey(aClass, propertyName);
        try {
            Field field = aClass.getField(propertyName);
            FIELD_CACHE.putIfAbsent(key, field);
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

    private static String mkKey(Object object, String propertyName) {
        return mkKey(object.getClass(), propertyName);
    }

    private static String mkKey(Class<?> clazz, String propertyName) {
        return clazz.getName() + "__" + propertyName;
    }

    // by not filling out the stack trace, we gain speed when using the exception as flow control
    private static boolean hasZeroArgs(Method mth) {
        return mth.getParameterCount() == 0;
    }

    private boolean takesSingleArgumentOnly(Method mth) {
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
