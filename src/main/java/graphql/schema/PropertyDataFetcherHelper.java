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
    private static final AtomicBoolean USE_DECLARED_ACCESSIBLE = new AtomicBoolean(true);
    private static final AtomicBoolean USE_NEGATIVE_CACHE = new AtomicBoolean(false);

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

        Method getterMethod = getPropertyGetterMethod(object, propertyName, graphQLType, dfeInUse);
        if (getterMethod != null) {
            try {
                if (takesDataFetcherEnvironmentAsOnlyArgument(getterMethod)) {
                    return getterMethod.invoke(object, environment);
                } else {
                    return getterMethod.invoke(object);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new GraphQLException(e);
            }

        }

        return getPropertyViaFieldAccess(object, propertyName);
    }


    private static Method getPropertyGetterMethod(Object object, String propertyName, GraphQLType graphQLType, boolean dfeInUse) {
        boolean isBoolProperty = isBooleanProperty(graphQLType);

        Method publiclyAccessibleMethod = findPubliclyAccessibleMethod(propertyName, object.getClass(), isBoolProperty, dfeInUse);
        if (publiclyAccessibleMethod != null) {
            return publiclyAccessibleMethod;
        }

        return findDeclaredAccessibleMethod(propertyName, object.getClass(), isBoolProperty, dfeInUse);
    }

    private static Method findPubliclyAccessibleMethod(String propertyName, Class<?> root, boolean isBoolProperty, boolean dfeInUse) {
        Class<?> currentClass = root;
        Method method = null;
        if (isBoolProperty) {
            String methodName = "is" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            method = getPubliclAccessibleMethod(currentClass, methodName, propertyName, dfeInUse);
        }

        if (method != null) {
            return method;
        }

        String methodName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        return getPubliclAccessibleMethod(currentClass, methodName, propertyName, dfeInUse);
    }

    private static Method getPubliclAccessibleMethod(Class<?> root, String methodName, String propertyName, boolean dfeInUse) {
        Class<?> currentClass = root;

        while (currentClass != null) {
            String key = mkKey(currentClass, propertyName);
            Method method = METHOD_CACHE.get(key);
            if (method != null) {
                return method;
            }

            if (Modifier.isPublic(currentClass.getModifiers())) {
                Method[] methods = currentClass.getMethods();
                if (dfeInUse) {
                    Optional<Method> methodOptional = Arrays.stream(methods).filter(mt -> mt.getName().equals(methodName) && takesDataFetcherEnvironmentAsOnlyArgument(mt)).findFirst();
                    if (methodOptional.isPresent()) {
                        if (Modifier.isPublic(methodOptional.get().getModifiers())) {
                            method = methodOptional.get();
                            METHOD_CACHE.putIfAbsent(key, method);
                            return method;
                        }
                    }
                }

                if (method == null) {
                    Optional<Method> methodOptional = Arrays.stream(methods).filter(mt -> mt.getName().equals(methodName)).findFirst();
                    if (methodOptional.isPresent()) {
                        if (Modifier.isPublic(methodOptional.get().getModifiers())) {
                            method = methodOptional.get();
                            METHOD_CACHE.putIfAbsent(key, method);
                            return method;
                        }
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    private static Method findDeclaredAccessibleMethod(String propertyName, Class<?> root, boolean isBoolProperty, boolean dfeInUse) {
        if (!USE_DECLARED_ACCESSIBLE.get()) {
            return null;
        }

        Class<?> currentClass = root;
        Method method = null;
        if (isBoolProperty) {
            String methodName = "is" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
            method = getDeclaredlAccessibleMethod(currentClass, methodName, propertyName, dfeInUse);
        }

        if (method != null) {
            return method;
        }

        String methodName = "get" + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        return getDeclaredlAccessibleMethod(currentClass, methodName, propertyName, dfeInUse);
    }

    private static Method getDeclaredlAccessibleMethod(Class<?> root, String methodName, String propertyName, boolean dfeInUse) {
        Class<?> currentClass = root;

        Predicate<Method> whichMethods = mth -> {
            if (dfeInUse) {
                return hasZeroArgs(mth) || takesDataFetcherEnvironmentAsOnlyArgument(mth);
            }
            return hasZeroArgs(mth);
        };

        while (currentClass != null) {
            String key = mkKey(currentClass, propertyName);
            Method method = METHOD_CACHE.get(key);
            if (method != null) {
                return method;
            }

            Method[] declaredMethods = currentClass.getDeclaredMethods();

            Optional<Method> m = Arrays.stream(declaredMethods)
                    .filter(mth -> methodName.equals(mth.getName()))
                    //whichMethods入参是一个Method: 是否没有参数、或者只有一个DataFetcherEnvironment参数
                    .filter(whichMethods)
                    //找到参数最少的方法
                    .min(mostMethodArgsFirst());
            //如果找到了方法，则加入缓存、并返回
            if (m.isPresent()) {
                // few JVMs actually enforce this but it might happen
                method = m.get();
                method.setAccessible(true);
                METHOD_CACHE.putIfAbsent(key, method);
                return method;
            }
            //在遍历父类方法、查找是否有getter
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }


    private static Object getPropertyViaFieldAccess(Object object, String propertyName) {
        Class<?> aClass = object.getClass();

        String key = mkKey(aClass, propertyName);
        Field field = FIELD_CACHE.get(key);
        if (field == null) {
            Field[] fields = aClass.getFields();
            Optional<Field> fieldOptional = Arrays.stream(fields).filter(x -> x.getName().equals(propertyName)).findFirst();
            if (fieldOptional.isPresent()) {
                field = fieldOptional.get();
                FIELD_CACHE.putIfAbsent(key, field);
                field = fieldOptional.get();
            }
        }

        if (field != null) {
            try {
                return field.get(object);
            } catch (IllegalAccessException e) {
                throw new GraphQLException(e);
            }
        }

        if (field == null && !USE_DECLARED_ACCESSIBLE.get()) {
            return null;
        }

        Field[] declaredFields = aClass.getDeclaredFields();
        Optional<Field> fieldOptional = Arrays.stream(declaredFields).filter(x -> x.getName().equals(propertyName)).findFirst();
        if (fieldOptional.isPresent()) {
            field = fieldOptional.get();
            FIELD_CACHE.putIfAbsent(key, field);
            field = fieldOptional.get();
        }

        if (field == null) {
            putInNegativeCache(key);
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(field);
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

    public static void clearNegativeCache() {
        NEGATIVE_CACHE.clear();
    }

    public static boolean setUseSetAccessible(boolean flag) {
        return USE_DECLARED_ACCESSIBLE.getAndSet(flag);
    }

    public static boolean setUseNegativeCache(boolean flag) {
        return USE_NEGATIVE_CACHE.getAndSet(flag);
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

    private static boolean takesDataFetcherEnvironmentAsOnlyArgument(Method mth) {
        return mth.getParameterCount() == 1 &&
                mth.getParameterTypes()[0].equals(DataFetchingEnvironment.class);
    }

    private static Comparator<? super Method> mostMethodArgsFirst() {
        return Comparator.comparingInt(Method::getParameterCount).reversed();
    }

}