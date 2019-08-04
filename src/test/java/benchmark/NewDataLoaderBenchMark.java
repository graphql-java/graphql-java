package benchmark;

import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * See http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/ for more samples
 * on what you can do with JMH
 *
 * You MUST have the JMH plugin for IDEA in place for this to work :  https://github.com/artyushov/idea-jmh-plugin
 *
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 5, batchSize = 3)
@Measurement(iterations = 3, time = 10, batchSize = 4)
public class NewDataLoaderBenchMark {

    static final BatchLoader batchLoadersAreStatelessAndCanBeReused = new BatchLoader() {
        @Override
        public CompletionStage<List> load(List keys) {
            return CompletableFuture.completedFuture(keys);
        }
    };

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMark_CreateDataLoaderRegistry_Throughput() {
        executeTest();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMark_CreateDataLoaderRegistry_AvgTime() {
        executeTest();
    }

    public static void executeTest() {

        DataLoaderRegistry registry = new DataLoaderRegistry();

        for (int i = 0; i < 60; i++) {
            DataLoader dataLoader = DataLoader.newDataLoader(batchLoadersAreStatelessAndCanBeReused);
            registry.register("" + i, dataLoader);
        }
    }
}
