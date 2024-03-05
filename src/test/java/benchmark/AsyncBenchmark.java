package benchmark;

import graphql.execution.Async;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 2, timeUnit = TimeUnit.NANOSECONDS)
@Fork(2)
public class AsyncBenchmark {

    @Param({"1", "5", "20"})
    public int numberOfFieldCFs;

    List<CompletableFuture<Object>> futures;

    @Setup(Level.Trial)
    public void setUp() throws ExecutionException, InterruptedException {
        futures = new ArrayList<>();
        for (int i = 0; i < numberOfFieldCFs; i++) {
            futures.add(mkFuture(i));
        }

    }

    private CompletableFuture<Object> mkFuture(int i) {
        return CompletableFuture.completedFuture(i);
    }


    @Benchmark
    @Warmup(iterations = 2, batchSize = 100)
    @Measurement(iterations = 2, batchSize = 100)
    public List<Object> benchmarkAsync() {
        Async.CombinedBuilder<Object> builder = Async.ofExpectedSize(futures.size());
        futures.forEach(builder::add);
        return builder.await().join();
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include("benchmark.AsyncBenchmark")
                .build();

        new Runner(opt).run();
    }

}
