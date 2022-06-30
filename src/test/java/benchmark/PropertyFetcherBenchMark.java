package benchmark;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * This benchmarks a simple property fetch to help improve the key class PropertyDataFetcher
 * <p>
 * See https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/ for more samples
 * on what you can do with JMH
 * <p>
 * You MUST have the JMH plugin for IDEA in place for this to work :  https://github.com/artyushov/idea-jmh-plugin
 * <p>
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 5, batchSize = 3)
@Measurement(iterations = 3, time = 10, batchSize = 4)
public class PropertyFetcherBenchMark {

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkThroughputInDirectClassHierarchy(Blackhole blackhole) {
        executeTest(blackhole, dfeFoo);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkThroughputDirectClassHierarchy(Blackhole blackhole) {
        executeTest(blackhole, dfeBar);
    }

    static PropertyDataFetcher<Object> nameFetcher = PropertyDataFetcher.fetching("name");

    static DataFetchingEnvironment dfeFoo = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(new Foo("brad")).build();
    static DataFetchingEnvironment dfeBar = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().source(new Bar("brad")).build();

    public static void executeTest(Blackhole blackhole, DataFetchingEnvironment dfe) {
        blackhole.consume(nameFetcher.get(dfe));
    }

    static class Foo extends Bar {

        Foo(String name) {
            super(name);
        }
    }

    static class Bar {
        private final String name;

        Bar(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
