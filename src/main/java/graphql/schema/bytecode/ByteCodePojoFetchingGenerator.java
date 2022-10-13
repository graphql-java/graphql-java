package graphql.schema.bytecode;

import graphql.Internal;
import graphql.VisibleForTesting;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Internal
public class ByteCodePojoFetchingGenerator {
    public static class NoMethodsException extends ByteCodeException {
        public NoMethodsException(Class<?> clazz) {
            super("There are no publicly accessible methods on " + clazz.getName());
        }
    }

    public static class CantGenerateClassException extends ByteCodeException {
        public CantGenerateClassException(Class<?> clazz, Exception e) {
            super("Unable to use Javassist to generate class for " + clazz.getName(), e);
        }
    }


    public static class Result {
        private final ByteCodeFetcher fetcher;
        private final Set<String> handledProperties;

        private Result(ByteCodeFetcher fetcher, Set<String> handledProperties) {
            this.fetcher = fetcher;
            this.handledProperties = handledProperties;
        }

        public ByteCodeFetcher getFetcher() {
            return fetcher;
        }

        public Set<String> getHandledProperties() {
            return handledProperties;
        }

        public boolean handlesProperty(String propertyName) {
            return handledProperties.contains(propertyName);
        }
    }

    public static Result generateClassFor(Class<?> sourceClass) {
        // this can throw exceptions so don't make any class code until we are ready
        Map<String, List<Method>> methods = getMethods(sourceClass);
        if (methods.isEmpty()) {
            return new Result(null,methods.keySet());
        }
        String methodBody = generateFetchMethodBody(sourceClass, methods);
        try {
            ClassPool pool = ClassPool.getDefault();
            pool.insertClassPath(new LoaderClassPath(ByteCodePojoFetchingGenerator.class.getClassLoader()));
            String className = mkClassName(sourceClass, pool);
            CtClass cc = pool.makeClass(className);
            CtClass ci = pool.get("graphql.schema.bytecode.ByteCodeFetcher");
            cc.setSuperclass(pool.get("java.lang.Object"));
            cc.addInterface(ci);

            CtMethod m = CtNewMethod.make(
                    methodBody,
                    cc);
            cc.addMethod(m);

            //noinspection unchecked
            Class<ByteCodeFetcher> clazz = (Class<ByteCodeFetcher>) cc.toClass();
            ByteCodeFetcher fetcher = clazz.newInstance();

            return new Result(fetcher, methods.keySet());
        } catch (Exception e) {
            throw new CantGenerateClassException(sourceClass, e);
        }
    }


    private static String mkClassName(Class<?> sourceClass, ClassPool pool) {
        int i = 0;
        while (true) {
            String name = ByteCodePojoFetchingGenerator.class.getPackage().getName() + ".Fetcher_4_" + sourceClass.getCanonicalName() + "Gen" + i;
            if (pool.getOrNull(name) == null) {
                return name;
            }
            i++;
        }
    }

    @VisibleForTesting
    static String generateFetchMethodBody(Class<?> sourceClass) {
        Map<String, List<Method>> methods = getMethods(sourceClass);
        return generateFetchMethodBody(sourceClass, methods);
    }

    private static String generateFetchMethodBody(Class<?> sourceClass, Map<String, List<Method>> methods) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        String canonicalName = sourceClass.getCanonicalName();


        out.println("public Object fetch(Object sourceObject, String propertyName) {");
        out.println("   if (sourceObject == null) { return null; }");
        out.printf("   %s source = (%s) sourceObject;\n", canonicalName, canonicalName);

        int count = 0;
        for (String propertyName : methods.keySet()) {
            Method method = appropriateMethod(propertyName, methods);
            String returnStatement = mkMethodCall(method);

            String ifOrElseIf = "} else if";
            if (count == 0) {
                ifOrElseIf = "if";
            }
            out.printf("" +
                    "   %s(\"%s\".equals(propertyName)) {\n" +
                    "      return %s;\n" +
                    "", ifOrElseIf, propertyName, returnStatement);

            count++;
        }
        out.println("" +
                "   } else {\n" +
                "      return null;\n" +
                "   }");

        out.println("}");
        return sw.toString();
    }

    private static Method appropriateMethod(String propertyName, Map<String, List<Method>> allMethods) {
        List<Method> methods = allMethods.get(propertyName);
        Method chosenMethod = methods.get(0);
        if (methods.size() == 1) {
            return chosenMethod;
        }
        Class<?> returnType = chosenMethod.getReturnType();
        if (returnType.equals(Boolean.class) || returnType.equals(Boolean.TYPE)) {
            chosenMethod = preferIsForBoolean(methods);
        }
        return chosenMethod;
    }

    private static Method preferIsForBoolean(List<Method> methods) {
        return methods.stream().filter(method -> method.getName().startsWith("is")).findFirst().orElse(methods.get(0));
    }

    private static Map<String, List<Method>> getMethods(Class<?> sourceClass) {
        // these will be in name order and in declaring class hierarchy order
        LinkedHashMap<String, List<Method>> mapOfMethods = getCandidateMethods(sourceClass).stream()
                .sorted(Comparator.comparing(Method::getName))
                .collect(
                        groupingBy(ByteCodePojoFetchingGenerator::mkPropertyName, LinkedHashMap::new, toList()));
        Iterator<Map.Entry<String, List<Method>>> iterator = mapOfMethods.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<Method>> entry = iterator.next();
            List<Method> methods = entry.getValue();
            boolean remove = false;
            boolean hasPublic = false;
            for (Method method : methods) {
                if (method.getParameterCount() > 0) {
                    // if any of the methods for a property take a parameter,
                    // then ignore them all
                    // we will rely on the old behavior for that
                    remove = true;
                }
                if (isPublic(method)) {
                    hasPublic = true;
                }
            }
            Method method = methods.get(0);
            if (!isPublic(method)) {
                // if the first method (first declaring class) is not public then we cant access the method
                // at call time - its possible for a declaring class to widen access
                remove = true;
            }
            if (!hasPublic) {
                remove = true;
            }
            if (remove) {
                iterator.remove();
            }
        }
        return mapOfMethods;
    }

    private static List<Method> getCandidateMethods(Class<?> sourceClass) {
        List<Method> methods = new ArrayList<>();
        Class<?> currentClass = sourceClass;
        while (currentClass != null) {
            Method[] declaredMethods = currentClass.getDeclaredMethods();
            for (Method declaredMethod : declaredMethods) {
                if (isPossiblePojoMethod(declaredMethod)) {
                    methods.add(declaredMethod);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return methods;
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
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private static String mkMethodCall(Method method) {
        Class<?> returnType = method.getReturnType();
        String methodCall = "source." + method.getName() + "()";
        if (returnType.isPrimitive()) {
            if (returnType.equals(Byte.TYPE)) {
                return "Integer.valueOf(" + methodCall + ")";
            } else if (returnType.equals(Short.TYPE)) {
                return "Integer.valueOf(" + methodCall + ")";
            } else if (returnType.equals(Integer.TYPE)) {
                return "Integer.valueOf(" + methodCall + ")";
            } else if (returnType.equals(Long.TYPE)) {
                return "Integer.valueOf(" + methodCall + ")";
            } else if (returnType.equals(Float.TYPE)) {
                return "Float.valueOf(" + methodCall + ")";
            } else if (returnType.equals(Double.TYPE)) {
                return "Double.valueOf(" + methodCall + ")";
            } else if (returnType.equals(Character.TYPE)) {
                return "Character.valueOf(" + methodCall + ")";
            } else {
                return "Boolean.valueOf(" + methodCall + ")";
            }
        } else {
            return methodCall;
        }
    }

    private static boolean isPossiblePojoMethod(Method method) {
        return !isObjectMethod(method) &&
                returnsSomething(method) &&
                isGetterNamed(method);
    }

    private static boolean isGetterNamed(Method method) {
        String name = method.getName();
        return ((name.startsWith("get") && name.length() > 4) || (name.startsWith("is") && name.length() > 3));
    }

    private static boolean returnsSomething(Method method) {
        return !method.getReturnType().equals(Void.class);
    }

    private static boolean isPublic(Method method) {
        return Modifier.isPublic(method.getModifiers()) && Modifier.isPublic(method.getDeclaringClass().getModifiers());
    }

    private static boolean isObjectMethod(Method method) {
        return method.getDeclaringClass().equals(Object.class);
    }
}
