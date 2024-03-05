package benchmark;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.PropertyDataFetcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 2, time = 5, batchSize = 50)
@Measurement(iterations = 3, batchSize = 50)
@Fork(3)
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
