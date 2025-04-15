package performance;


import graphql.util.InterThreadMemoizedSupplier;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(1)
@Threads(Threads.MAX)
public class InterThreadMemoizedPerformance {


    @State(Scope.Benchmark)
    public static class MyState {

        InterThreadMemoizedSupplier<String> supplier;
        final String value = "Hello World";

        @Setup
        public void setup() {
            supplier = new InterThreadMemoizedSupplier<>(() -> {
                return "Hello World";
            });

        }
    }


    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void accessSupplier(MyState state, Blackhole blackhole) {
        String result = state.supplier.get();
        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void accessFinalValue(MyState state, Blackhole blackhole) {
        blackhole.consume(state.value);
    }

}
