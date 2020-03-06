package benchmark;

import graphql.execution.ExecutionPath;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class ExecutionPathBenchMark {

    private static final List<String> sixLenPath = Arrays.asList("parent", "child", "grandchild", "greatgrandchild", "greatgreatgrandchild", "greatgreatgreatgrandchild");
    private static final List<String> fiveLenPath = Arrays.asList("parent", "child", "grandchild", "greatgrandchild", "greatgreatgrandchild");

    private static ExecutionPath sixPath = mkPath(sixLenPath);
    private static List<ExecutionPath> sixers = Arrays.asList(sixPath, sixPath, sixPath, sixPath);
    private static List<ExecutionPath> fivers = Arrays.asList(mkPath(fiveLenPath), mkPath(fiveLenPath), mkPath(fiveLenPath), mkPath(fiveLenPath));


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 2, time = 1, batchSize = 3)
    @Measurement(iterations = 3, time = 5, batchSize = 5)
    public void benchExecutionPathHandling() {

        mkPath(sixLenPath);

        // use .equals
        //noinspection ResultOfMethodCallIgnored
        sixers.equals(fivers);

        // use hash code
        new HashSet<>(sixers);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 2, time = 1, batchSize = 3)
    @Measurement(iterations = 3, time = 5, batchSize = 5)
    public void toListBenchmarking() {
        sixPath.toList();
    }

    static ExecutionPath mkPath(List<String> segments) {
        ExecutionPath path = ExecutionPath.rootPath();
        for (String segment : segments) {
            path = path.segment(segment);
        }
        return path;
    }
}
