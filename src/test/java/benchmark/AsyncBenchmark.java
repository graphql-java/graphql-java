package benchmark;

import graphql.execution.Async;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
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
public class AsyncBenchmark {

    @Param({"0", "1", "10"})
    public int num;

    List<CompletableFuture<Object>> futures;

    @Setup(Level.Trial)
    public void setUp() throws ExecutionException, InterruptedException {
        futures = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            futures.add(mkFuture(i));
        }

    }

    private CompletableFuture<Object> mkFuture(int i) {
        // half will take some time
        if (i % 2 == 0) {
            return CompletableFuture.supplyAsync(() -> sleep(i));
        } else {
            return CompletableFuture.completedFuture(i);
        }
    }

    private Object sleep(int i) {
        try {
            Thread.sleep(i * 1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return i;
    }

    @Benchmark
    public List<Object> benchmarkAsync() {
        Async.CombinedBuilder<Object> builder = Async.ofExpectedSize(futures.size());
        futures.forEach(builder::add);
        return builder.await().join();
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include("benchmark.AsyncBenchmark")
                .forks(5)
                .build();

        new Runner(opt).run();
    }

}
