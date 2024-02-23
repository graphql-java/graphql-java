package benchmark;

import graphql.schema.fetching.LambdaFetchingSupport;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

@Warmup(iterations = 2, time = 5, batchSize = 500)
@Measurement(iterations = 3, batchSize = 500)
@Fork(3)
public class GetterAccessBenchmark {

    public static class Pojo {
        final String name;
        final int age;

        public Pojo(String name, int age) {
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

    static Pojo pojo = new Pojo("Brad", 42);

    static Function<Object, Object> getter = LambdaFetchingSupport.createGetter(Pojo.class, "name").get();

    static Method getterMethod;

    static {
        try {
            getterMethod = Pojo.class.getMethod("getName");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }


    @Benchmark
    public void measureDirectAccess(Blackhole bh) {
        Object name = pojo.getName();
        bh.consume(name);
    }

    @Benchmark
    public void measureLambdaAccess(Blackhole bh) {
        Object value = getter.apply(pojo);
        bh.consume(value);
    }

    @Benchmark
    public void measureReflectionAccess(Blackhole bh) {
        try {
            Object name = getterMethod.invoke(pojo);
            bh.consume(name);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}

