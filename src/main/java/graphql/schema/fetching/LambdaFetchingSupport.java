package graphql.schema.fetching;

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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

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
                //
                // if we cant make a dynamic lambda here, then we give up and let the old property fetching code do its thing
                // this can happen on runtimes such as GraalVM native where LambdaMetafactory is not supported
                // and will throw something like :
                //
                //    com.oracle.svm.core.jdk.UnsupportedFeatureError: Defining hidden classes at runtime is not supported.
                //        at org.graalvm.nativeimage.builder/com.oracle.svm.core.util.VMError.unsupportedFeature(VMError.java:89)
            }
        }
        return Optional.empty();
    }


    private static Method getCandidateMethod(Class<?> sourceClass, String propertyName) {
        // property() methods first
        Predicate<Method> recordLikePredicate = method -> isRecordLike(method) && propertyName.equals(decapitalize(method.getName()));
        List<Method> recordLikeMethods = findMethodsForProperty(sourceClass,
                recordLikePredicate);
        if (!recordLikeMethods.isEmpty()) {
            return recordLikeMethods.get(0);
        }

        // getProperty() POJO methods next
        Predicate<Method> getterPredicate = method -> isGetterNamed(method) && propertyName.equals(mkPropertyNameGetter(method));
        List<Method> allGetterMethods = findMethodsForProperty(sourceClass,
                getterPredicate);
        List<Method> pojoGetterMethods = allGetterMethods.stream()
                .filter(LambdaFetchingSupport::isPossiblePojoMethod)
                .collect(toList());
        if (!pojoGetterMethods.isEmpty()) {
            Method method = pojoGetterMethods.get(0);
            if (isBooleanGetter(method)) {
                method = findBestBooleanGetter(pojoGetterMethods);
            }
            return checkForSingleParameterPeer(method, allGetterMethods);
        }
        return null;
    }

    private static Method checkForSingleParameterPeer(Method candidateMethod, List<Method> allMethods) {
        // getFoo(DataFetchingEnv ev) is allowed, but we don't want to handle it in this class
        // so this find those edge cases
        for (Method allMethod : allMethods) {
            if (allMethod.getParameterCount() > 0) {
                // we have some method with the property name that takes more than 1 argument
                // we don't want to handle this here, so we are saying there is one
                return null;
            }
        }
        return candidateMethod;
    }

    private static Method findBestBooleanGetter(List<Method> methods) {
        // we prefer isX() over getX() if both happen to be present
        Optional<Method> isMethod = methods.stream().filter(method -> method.getName().startsWith("is")).findFirst();
        return isMethod.orElse(methods.get(0));
    }

    /**
     * Finds all methods in a class hierarchy that match the property name - they might not be suitable but they
     *
     * @param sourceClass the class we are looking to work on
     *
     * @return a list of getter methods for that property
     */
    private static List<Method> findMethodsForProperty(Class<?> sourceClass, Predicate<Method> predicate) {
        List<Method> methods = new ArrayList<>();
        Class<?> currentClass = sourceClass;
        while (currentClass != null) {
            Method[] declaredMethods = currentClass.getDeclaredMethods();
            for (Method declaredMethod : declaredMethods) {
                if (predicate.test(declaredMethod)) {
                    methods.add(declaredMethod);
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        return methods.stream()
                .sorted(Comparator.comparing(Method::getName))
                .collect(toList());
    }

    private static boolean isPossiblePojoMethod(Method method) {
        return !isObjectMethod(method) &&
                returnsSomething(method) &&
                isGetterNamed(method) &&
                hasNoParameters(method) &&
                isPublic(method);
    }

    private static boolean isRecordLike(Method method) {
        return !isObjectMethod(method) &&
                returnsSomething(method) &&
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

    private static String mkPropertyNameGetter(Method method) {
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
        MethodHandles.Lookup lookup = getLookup(targetClass);
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

    private static MethodHandles.Lookup getLookup(Class<?> targetClass) {
        MethodHandles.Lookup lookupMe = MethodHandles.lookup();
        //
        // This is a Java 9+ approach to method look up allowing private access
        //
        try {
            return MethodHandles.privateLookupIn(targetClass, lookupMe);
        } catch (IllegalAccessException e) {
            return lookupMe;
        }
    }

}
