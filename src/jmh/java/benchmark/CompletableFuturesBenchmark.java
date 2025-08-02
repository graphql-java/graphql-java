package benchmark;

import com.google.common.collect.ImmutableList;
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 10, batchSize = 10)
@Fork(3)
public class CompletableFuturesBenchmark {


    @Param({"2", "5"})
    public int depth;
    public int howMany = 10;

    @Setup(Level.Trial)
    public void setUp() {
    }

    private List<CompletableFuture<Object>> mkCFObjects(int howMany, int depth) {
        if (depth <= 0) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<CompletableFuture<Object>> builder = ImmutableList.builder();
        for (int i = 0; i < howMany; i++) {
            CompletableFuture<Object> cf = CompletableFuture.completedFuture(mkCFObjects(howMany, depth - 1));
            builder.add(cf);
        }
        return builder.build();
    }

    private List<Object> mkObjects(int howMany, int depth) {
        if (depth <= 0) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<Object> builder = ImmutableList.builder();
        for (int i = 0; i < howMany; i++) {
            Object obj = mkObjects(howMany, depth - 1);
            builder.add(obj);
        }
        return builder.build();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkCFApproach() {
        // make results
        List<CompletableFuture<Object>> completableFutures = mkCFObjects(howMany, depth);
        // traverse results
        traverseCFS(completableFutures);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkMaterializedApproach() {
        // make results
        List<Object> objects = mkObjects(howMany, depth);
        // traverse results
        traverseObjects(objects);
    }

    @SuppressWarnings("unchecked")
    private void traverseCFS(List<CompletableFuture<Object>> completableFutures) {
        for (CompletableFuture<Object> completableFuture : completableFutures) {
            // and when it's done - visit its child results - which are always immediate on completed CFs
            // so this whenComplete executed now
            completableFuture.whenComplete((list, t) -> {
                List<CompletableFuture<Object>> cfs = (List<CompletableFuture<Object>>) list;
                traverseCFS(cfs);
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void traverseObjects(List<Object> objects) {
        for (Object object : objects) {
            List<Object> list = (List<Object>) object;
            traverseObjects(list);
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include("benchmark.CompletableFuturesBenchmark")
                .build();

        new Runner(opt).run();
    }

}
