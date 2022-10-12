package benchmark;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Warmup(iterations = 2, time = 2, batchSize = 3)
@Measurement(iterations = 3, time = 5, batchSize = 4)
public class ByteCodeGen {


    static public class Customer {
        final String name;
        final int age;

        Customer(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    static class DFInterceptor implements DFFetcher {
        public Object fetchImpl(Customer source, String propertyName) {
            if ("name".equals(propertyName)) {
                return source.getName();
            }
            if ("age".equals(propertyName)) {
                return Integer.valueOf(source.getAge());
            }
            return null;
        }

        @Override
        public Object fetch(Object source, String propertyName) {
            return fetchImpl((Customer) source, propertyName);
        }
    }

    interface DFFetcher {
        Object fetch(Object source, String propertyName);

    }


    static Class<?> generatedClass;
    static Customer customer1;

    static DFFetcher fetcher;

    static Method ageMethod;

    static {
        generatedClass = defineClass();
        customer1 = new Customer("Brad", 53);
        try {
            ageMethod = Customer.class.getDeclaredMethod("getAge");
            fetcher = (DFFetcher) generatedClass.newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    @Benchmark
    public void measureViaReflectedMethod(Blackhole bh) throws InvocationTargetException, IllegalAccessException {
        Object value = ageMethod.invoke(customer1);
        bh.consume(value);
    }
    @Benchmark
    public void measureViaGeneratedClass(Blackhole bh) throws InvocationTargetException, IllegalAccessException {
        Object value = fetcher.fetch(customer1,"age");
        bh.consume(value);
    }

    private static Class<?> defineClass() {
        try {
            ClassPool pool = ClassPool.getDefault();

            pool.insertClassPath(new LoaderClassPath(ByteCodeGen.class.getClassLoader()));
            CtClass cc = pool.makeClass("benchmark.DF");
            CtClass ci = pool.get("benchmark.ByteCodeGen$DFFetcher");
            cc.setSuperclass(pool.get("java.lang.Object"));
            cc.addInterface(ci);

            String methodBody = "" +
                    "        public Object fetch(Object sourceObj, String propertyName) {\n" +
                    "            benchmark.ByteCodeGen.Customer source = (benchmark.ByteCodeGen.Customer) sourceObj;\n" +
                    "            if (\"name\".equals(propertyName)) {\n" +
                    "                return source.getName();\n" +
                    "            }\n" +
                    "            if (\"age\".equals(propertyName)) {\n" +
                    "                return Integer.valueOf(source.getAge());\n" +
                    "            }\n" +
                    "            return null;\n" +
                    "        }\n";
            CtMethod m = CtNewMethod.make(
                    methodBody,
                    cc);
            cc.addMethod(m);

            return cc.toClass();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void mainX(String[] args) throws NotFoundException, CannotCompileException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {

        Object value1 = fetcher.fetch(customer1,"age");
        Object value2 = ageMethod.invoke(customer1);




    }
}
