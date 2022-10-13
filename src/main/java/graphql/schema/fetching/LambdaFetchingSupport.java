package graphql.schema.fetching;

import graphql.Assert;
import graphql.Internal;
import graphql.VisibleForTesting;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Internal
public class LambdaFetchingSupport {


    /**
     * This support class will use {@link LambdaMetafactory} and {@link MethodHandles} to create a dynamic function that allows access to a public
     * getter method on the nominated class.  {@link MethodHandles} is a caller senstive lookup mechanism.  If the graphql-java cant lookup a class, then
     * it won't be able to make dynamic lambda function to it.
     * <p>
     * If one cant be made, because it doesn't exist or the calling class does not have access to the method, then it will return
     * an empty result indicating that this strategy cant be used.
     *
     * @param sourceClass  the class that has the property getter method
     * @param propertyName the name of the property to get
     *
     * @return a function that can be used to pass in an instance of source class and returns its getter method value
     */
    public static Optional<Function<Object, Object>> createGetter(Class<?> sourceClass, String propertyName) {
        Method candidateMethod = getCandidateMethod(sourceClass, propertyName);
        if (candidateMethod != null) {
            try {
                Function<Object, Object> getterFunction = mkCallFunction(sourceClass, candidateMethod.getName(), candidateMethod.getReturnType());
                return Optional.of(getterFunction);
            } catch (Throwable ignore) {
                // if we can make a dynamic lambda here, then we give up and let the old property fetching code do its thing
            }
        }
        return Optional.empty();
    }


    private static Method getCandidateMethod(Class<?> sourceClass, String propertyName) {
        Class<?> currentClass = sourceClass;
        while (currentClass != null) {
            Method[] declaredMethods = currentClass.getDeclaredMethods();
            for (Method declaredMethod : declaredMethods) {
                if (isPossiblePojoMethod(declaredMethod)) {
                    if (nameMatches(propertyName, declaredMethod)) {
                        if (isBooleanGetter(declaredMethod)) {
                            declaredMethod = findBestBooleanGetter(sourceClass, propertyName);
                        }
                        return declaredMethod;
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    private static Method findBestBooleanGetter(Class<?> sourceClass, String propertyName) {
        List<Method> boolGetters = new ArrayList<>();
        Class<?> currentClass = sourceClass;
        while (currentClass != null) {
            Method[] declaredMethods = currentClass.getDeclaredMethods();
            for (Method declaredMethod : declaredMethods) {
                if (isPossiblePojoMethod(declaredMethod)) {
                    if (nameMatches(propertyName, declaredMethod)) {
                        if (isBooleanGetter(declaredMethod)) {
                            boolGetters.add(declaredMethod);
                        }
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        Assert.assertFalse(boolGetters.isEmpty(), () ->
                "How did it come to this?");
        Optional<Method> isMethod = boolGetters.stream().filter(method -> method.getName().startsWith("is")).findFirst();
        return isMethod.orElse(boolGetters.get(0));
    }

    private static boolean nameMatches(String propertyName, Method declaredMethod) {
        String methodPropName = mkPropertyName(declaredMethod);
        return propertyName.equals(methodPropName);
    }

    private static boolean isPossiblePojoMethod(Method method) {
        return !isObjectMethod(method) &&
                returnsSomething(method) &&
                isGetterNamed(method) &&
                hasNoParameters(method) &&
                isPublic(method);
    }

    private static boolean isBooleanGetter(Method method) {
        Class<?> returnType = method.getReturnType();
        return isGetterNamed(method) && (returnType.equals(Boolean.class) || returnType.equals(Boolean.TYPE));
    }

    private static boolean hasNoParameters(Method method) {
        return method.getParameterCount() == 0;
    }

    private static boolean isGetterNamed(Method method) {
        String name = method.getName();
        return ((name.startsWith("get") && name.length() > 4) || (name.startsWith("is") && name.length() > 3));
    }

    private static boolean returnsSomething(Method method) {
        return !method.getReturnType().equals(Void.class);
    }

    private static boolean isPublic(Method method) {
        return Modifier.isPublic(method.getModifiers());
    }

    private static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass().equals(Object.class);
    }

    private static String mkPropertyName(Method method) {
        //
        // getFooName becomes fooName
        // isFoo becomes foo
        //
        String name = method.getName();
        if (name.startsWith("get")) {
            name = name.substring(3);
        } else if (name.startsWith("is")) {
            name = name.substring(2);
        }
        return decapitalize(name);
    }

    private static String decapitalize(String name) {
        if (name.length() == 0) {
            return name;
        }
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }


    @VisibleForTesting
    static Function<Object, Object> mkCallFunction(Class<?> targetClass, String targetMethod, Class<?> targetMethodReturnType) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle virtualMethodHandle = lookup.findVirtual(targetClass, targetMethod, MethodType.methodType(targetMethodReturnType));
        CallSite site = LambdaMetafactory.metafactory(lookup,
                "apply",
                MethodType.methodType(Function.class),
                MethodType.methodType(Object.class, Object.class),
                virtualMethodHandle,
                MethodType.methodType(targetMethodReturnType, targetClass));
        @SuppressWarnings("unchecked")
        Function<Object, Object> getterFunction = (Function<Object, Object>) site.getTarget().invokeExact();
        return getterFunction;
    }

}
