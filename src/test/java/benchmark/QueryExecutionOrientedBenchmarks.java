package benchmark;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * A runner of benchmarks that are whole query runners and they do
 * so from the top of the stack all the way in
 */
public class QueryExecutionOrientedBenchmarks {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include("benchmark.ComplexQueryBenchmark")
                .include("benchmark.IntrospectionBenchmark")
                .include("benchmark.TwitterBenchmark")
                .build();

        new Runner(opt).run();
    }
}
