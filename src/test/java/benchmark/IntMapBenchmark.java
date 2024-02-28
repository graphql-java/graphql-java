package benchmark;

import graphql.execution.instrumentation.dataloader.LevelMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.LinkedHashMap;
import java.util.Map;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class IntMapBenchmark {

    @Benchmark
    public void benchmarkLinkedHashMap(Blackhole blackhole) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            int level = i % 10;
            int count = i * 2;
            result.put(level, result.getOrDefault(level, 0) + count);
            blackhole.consume(result.get(level));
        }
    }

    @Benchmark
    public void benchmarkIntMap(Blackhole blackhole) {
        LevelMap result = new LevelMap(16);
        for (int i = 0; i < 30; i++) {
            int level = i % 10;
            int count = i * 2;
            result.increment(level, count);
            blackhole.consume(result.get(level));
        }
    }
}

