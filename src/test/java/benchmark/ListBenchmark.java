package benchmark;

import com.google.common.collect.ImmutableList;
import graphql.collect.ImmutableKit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class ListBenchmark {

    static final List<String> startingList = buildStartingList();

    private static List<String> buildStartingList() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            list.add("String" + i);
        }
        return list;
    }

    private final Function<String, String> mapper = s -> new StringBuilder(s).reverse().toString();

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
