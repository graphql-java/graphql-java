package benchmark.graphql.scalar;


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 2, time = 2, batchSize = 2)
@Measurement(iterations = 2, time = 2, batchSize = 2)
public class CoercingUtilBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void isNumberIsh1(Blackhole blackhole) {
        for (int i = 0; i < 10; i++) {
            boolean result = CoercingUtilInfo.isNumberIsh1(i);
            blackhole.consume(result);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void isNumberIsh2(Blackhole blackhole) {
        for (int i = 0; i < 10; i++) {
            boolean result = CoercingUtilInfo.isNumberIsh2(i);
            blackhole.consume(result);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void isNumberIsh1WithException(Blackhole blackhole) {
        for (int i = 0; i < 10; i++) {
            Object obj = "notNumber";

            if (CoercingUtilInfo.isNumberIsh1(i)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(obj.toString());
                } catch (NumberFormatException e) {
                    continue;
                }
                try {
                    blackhole.consume(value.intValueExact());
                } catch (ArithmeticException e) {
                    // ignored
                }
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void isNumberIsh2WithException(Blackhole blackhole) {
        for (int i = 0; i < 10; i++) {
            Object obj = "notNumber";

            if (CoercingUtilInfo.isNumberIsh2(i)) {
                BigDecimal value;
                try {
                    value = new BigDecimal(obj.toString());
                } catch (NumberFormatException e) {
                    continue;
                }
                try {
                    blackhole.consume(value.intValueExact());
                } catch (ArithmeticException e) {
                    // ignored
                }
            }
        }
    }


    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder().include(CoercingUtilBenchmark.class.getSimpleName()).build();
        new Runner(options).run();
    }

}
