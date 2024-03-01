package benchmark;

import com.google.common.collect.ImmutableMap;
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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1, batchSize = 1000)
@Fork(3)
public class MapBenchmark {

    @Param({"10", "50", "300"})
    int numberEntries = 300;

    Map<String, Integer> hashMap;
    Map<String, Integer> linkedHashMap;
    Map<String, Integer> immutableMap;

    Random random;

    @Setup(Level.Trial)
    public void setUp() {
        random = new Random();
        linkedHashMap = new LinkedHashMap<>();
        for (int i = 0; i < numberEntries; i++) {
            linkedHashMap.put("string" + i, i);
        }
        hashMap = new HashMap<>();
        for (int i = 0; i < numberEntries; i++) {
            hashMap.put("string" + i, i);
        }
        ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
        for (int i = 0; i < numberEntries; i++) {
            builder.put("string" + i, i);
        }
        immutableMap = builder.build();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkLinkedHashMap(Blackhole blackhole) {
        mapGet(blackhole, linkedHashMap);
    }
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkHashMap(Blackhole blackhole) {
        mapGet(blackhole, hashMap);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void benchmarkImmutableMap(Blackhole blackhole) {
        mapGet(blackhole, immutableMap);
    }

    private void mapGet(Blackhole blackhole, Map<String, Integer> mapp) {
        int index = rand(0, numberEntries);
        blackhole.consume(mapp.get("string" + index));
    }

    private int rand(int loInc, int hiExc) {
        return random.nextInt(hiExc - loInc) + loInc;
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include("benchmark.MapBenchmark")
                .build();

        new Runner(opt).run();
    }
}

