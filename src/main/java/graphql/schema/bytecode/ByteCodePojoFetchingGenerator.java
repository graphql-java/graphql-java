package graphql.schema.bytecode;

import graphql.Internal;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Internal
public class ByteCodePojoFetchingGenerator {

    public static class DualPropertyException extends ByteCodeException {
        public DualPropertyException(String property) {
            super("The property '" + property + "' has two definitions");
        }
    }

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


    public static Class<ByteCodeFetcher> generateClassFor(Class<?> sourceClass) {
        try {
            ClassPool pool = ClassPool.getDefault();

            pool.insertClassPath(new LoaderClassPath(ByteCodePojoFetchingGenerator.class.getClassLoader()));
            CtClass cc = pool.makeClass("graphql.schema.bytecode." + mkClassName(sourceClass));
            CtClass ci = pool.get("graphql.schema.bytecode.ByteCodeFetcher");
            cc.setSuperclass(pool.get("java.lang.Object"));
            cc.addInterface(ci);

            String methodBody = generateMethodBody(sourceClass);
            CtMethod m = CtNewMethod.make(
                    methodBody,
                    cc);
            cc.addMethod(m);
            //noinspection unchecked
            return (Class<ByteCodeFetcher>) cc.toClass();
        } catch (Exception e) {
            throw new CantGenerateClassException(sourceClass, e);
        }
    }

    private static String mkClassName(Class<?> sourceClass) {
        return ByteCodePojoFetchingGenerator.class.getPackage().getName() + ".Fetcher4" + sourceClass.getCanonicalName() + "Gen";
    }

    public static String generateMethodBody(Class<?> sourceClass) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        String canonicalName = sourceClass.getCanonicalName();


        out.println("public Object fetch(Object sourceObject, String propertyName) {");
        out.println("   if (sourceObject == null) { return null; }");
        out.printf("   %s source = (%s) sourceObject;\n", canonicalName, canonicalName);

        Set<String> properties = new HashSet<>();
        List<Method> methods = Arrays.stream(sourceClass.getMethods())
                .sorted(Comparator.comparing(Method::getName))
                .collect(Collectors.toList());
        for (Method method : methods) {
            if (isPojoMethod(method)) {
                String propertyName = mkPropertyName(method);
                if (properties.contains(propertyName)) {
                    throw new DualPropertyException(propertyName);
                }

                String returnStatement = mkMethodCall(method);

                String ifOrElseIf = "} else if";
                if (properties.isEmpty()) {
                    ifOrElseIf = "if";
                }
                out.printf("" +
                        "   %s(\"%s\".equals(propertyName)) {\n" +
                        "      return %s;\n" +
                        "", ifOrElseIf, propertyName, returnStatement);

                properties.add(propertyName);
            }
        }
        if (properties.isEmpty()) {
            throw new NoMethodsException(sourceClass);
        }
        out.println("" +
                "   } else {\n" +
                "      return null;\n" +
                "   }");

        out.println("}");
        return sw.toString();
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
        return name.substring(0, 1).toLowerCase() + name.substring(1);
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

    private static boolean isPojoMethod(Method method) {
        return isPublic(method) &&
                !isObjectMethod(method) &&
                returnsSomething(method) &&
                isGetter(method);
    }

    private static boolean isGetter(Method method) {
        String name = method.getName();
        return method.getParameterCount() == 0 &&
                ((name.startsWith("get") && name.length() > 4) || (name.startsWith("is") && name.length() > 3));
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
