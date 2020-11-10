package benchmark;

import com.google.common.collect.ImmutableList;
import graphql.collect.ImmutableKit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 2, timeUnit = TimeUnit.NANOSECONDS)
public class ListBenchmark {

    static final List<String> startingList = buildStartingList();

    private static List<String> buildStartingList() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            list.add("String" + i);
        }
        return list;
    }

    Function<String, String> mapper = s -> new StringBuilder(s).reverse().toString();

    @Benchmark
    public void benchmarkListStream(Blackhole blackhole) {
        List<String> output = startingList.stream().map(mapper).collect(Collectors.toList());
        blackhole.consume(output);
    }

    @Benchmark
    public void benchmarkImmutableListBuilder(Blackhole blackhole) {
        List<String> output = ImmutableKit.map(startingList, mapper);
        blackhole.consume(output);
    }

    @Benchmark
    public void benchmarkArrayList(Blackhole blackhole) {
        List<String> output = new ArrayList<>(startingList.size());
        for (String s : startingList) {
            output.add(mapper.apply(s));
        }
        blackhole.consume(output);
    }

    @Benchmark
    public void benchmarkImmutableCollectorBuilder(Blackhole blackhole) {
        List<String> output = startingList.stream().map(mapper).collect(ImmutableList.toImmutableList());
        blackhole.consume(output);
    }

}
