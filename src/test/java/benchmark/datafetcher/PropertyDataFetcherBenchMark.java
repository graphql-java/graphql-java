package benchmark.datafetcher;

import graphql.Assert;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;


public class PropertyDataFetcherBenchMark {

    public static void main(String[] args) throws RunnerException {
        Options build = new OptionsBuilder().
                include(PropertyDataFetcherBenchMark.class.getSimpleName())
                .mode(Mode.SampleTime).timeUnit(TimeUnit.MICROSECONDS).threads(5).forks(1).build();

        new Runner(build).run();
    }

    @Benchmark
    public void fetchingData(DataFetcherState dataFetcherState, Blackhole blackhole) throws Exception {
        Object publicProperty = dataFetcherState.publicPropertyFetcher.get(dataFetcherState.environment);
//        assertEqualString(publicProperty.toString(),"publicValue");
        blackhole.consume(publicProperty);
        Object privateProperty = dataFetcherState.privatePropertyFetcher.get(dataFetcherState.environment);
//        assertEqualString(privateProperty.toString(),"privateValue");
        blackhole.consume(privateProperty);
        Object publicField = dataFetcherState.publicFieldFetcher.get(dataFetcherState.environment);
//        assertEqualString(publicField.toString(),"publicFieldValue");
        blackhole.consume(publicField);
        Object privateField = dataFetcherState.privateFieldValueFetcher.get(dataFetcherState.environment);
//        assertEqualString(privateField.toString(),"privateFieldValue");
        blackhole.consume(privateField);
        Object unknownProperty = dataFetcherState.unknownPropertyFetcher.get(dataFetcherState.environment);
//        Assert.assertNull(unknownProperty);
        blackhole.consume(unknownProperty);
    }

//    public static void assertEqualString(String source, String target) throws Exception {
//        if (source == null || target == null) {
//            throw new Exception("value is null");
//        }
//        if (!source.equals(target)) {
//            throw new Exception("value not equals"+source+target);
//        }
//    }
}
