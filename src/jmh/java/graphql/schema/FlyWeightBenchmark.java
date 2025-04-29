package graphql.schema;

import benchmark.BenchmarkUtils;
import graphql.util.flyweight.FlyweightKit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 2, time = 5, batchSize = 50)
@Measurement(iterations = 3, batchSize = 50)
@Fork(3)
public class FlyWeightBenchmark {

    private static final int LOOPS = 10;

    static FlyweightKit.BiKeyMap<String, String, FieldCoordinates> flyweightFieldCoordinates = new FlyweightKit.BiKeyMap<>();
    static FlyweightKit.TriKeyMap<ClassLoader, String, String, PropertyFetchingImpl.CacheKey> FLYWEIGHT_CACHE = new FlyweightKit.TriKeyMap<>();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkDirectInstantiationThroughput(Blackhole blackhole) {
        directInstantiation(blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkDirectInstantiationAvgTime(Blackhole blackhole) {
        directInstantiation(blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkUsingFlyweightsThroughput(Blackhole blackhole) {
        flyweights(blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkUsingFlyweightsAveTime(Blackhole blackhole) {
        flyweights(blackhole);
    }

    private void directInstantiation(Blackhole blackhole) {
        for (int i = 0; i < LOOPS; i++) {
            FieldCoordinates fieldCoordinates = FieldCoordinates.coordinates(
                    mkName("t", i),
                    "f");
            blackhole.consume(fieldCoordinates);

            PropertyFetchingImpl.CacheKey cacheKey = new PropertyFetchingImpl.CacheKey(
                    getClass().getClassLoader(),
                    mkName("c", i),
                    "f");
            blackhole.consume(cacheKey);
        }
    }

    private void flyweights(Blackhole blackhole) {
        for (int i = 0; i < LOOPS; i++) {
            FieldCoordinates fieldCoordinates = flyweightFieldCoordinates.computeIfAbsent(
                    mkName("t", i),
                    "f",
                    FieldCoordinates::coordinates);
            blackhole.consume(fieldCoordinates);

            PropertyFetchingImpl.CacheKey cacheKey = FLYWEIGHT_CACHE.computeIfAbsent(
                    getClass().getClassLoader(),
                    mkName("c", i),
                    "f",
                    PropertyFetchingImpl.CacheKey::new);
            blackhole.consume(cacheKey);
        }
    }

    private String mkName(String s, int i) {
        return s + "_" + (i % 2 == 0);
    }


    public static void main(String[] args) throws RunnerException {
        runAtStartup();
        Options opt = new OptionsBuilder()
                .include("graphql.schema.FlyWeightBenchmark")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(opt).run();
    }

    private static void runAtStartup() {
        FlyWeightBenchmark benchMark = new FlyWeightBenchmark();
        Blackhole blackhole = BenchmarkUtils.blackHole();

        BenchmarkUtils.runInToolingForSomeTimeThenExit(
                () -> {
                },
                () -> benchMark.benchMarkUsingFlyweightsThroughput(blackhole),
                () -> {
                }

        );
    }
}
