package benchmark;

import graphql.Assert;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 2, time = 5, batchSize = 50)
@Measurement(iterations = 3, batchSize = 50)
@Fork(3)
public class AssertBenchmark {

    private static final int LOOPS = 100;
    private static final boolean BOOL = new Random().nextBoolean();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAssertWithString() {
        for (int i = 0; i < LOOPS; i++) {
            Assert.assertTrue(jitTrue(), "This string is constant");
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAssertWithStringSupplier() {
        for (int i = 0; i < LOOPS; i++) {
            Assert.assertTrue(jitTrue(), () -> "This string is constant");
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAssertWithStringSupplierFormatted() {
        for (int i = 0; i < LOOPS; i++) {
            final int captured = i;
            Assert.assertTrue(jitTrue(), () -> String.format("This string is not constant %d", captured));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAssertWithStringFormatted() {
        for (int i = 0; i < LOOPS; i++) {
            Assert.assertTrue(jitTrue(), "This string is not constant %d", i);
        }
    }

    private boolean jitTrue() {
        // can you jit this away, Mr JIT??
        //noinspection ConstantValue,SimplifiableConditionalExpression
        return BOOL ? BOOL : !BOOL;
    }

    public static void main(String[] args) throws RunnerException {
        runAtStartup();
        Options opt = new OptionsBuilder()
                .include("benchmark.AssertBenchmark")
                .build();

        new Runner(opt).run();
    }

    private static void runAtStartup() {
        AssertBenchmark benchMark = new AssertBenchmark();
        BenchmarkUtils.runInToolingForSomeTimeThenExit(
                () -> {
                },
                benchMark::benchMarkAssertWithStringSupplier,
                () -> {
                }

        );
    }
}
