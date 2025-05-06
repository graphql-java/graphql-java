package graphql.execution;

import graphql.Scalars;
import graphql.language.Field;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 2)
@Fork(2)
public class ExecutionStepInfoBenchmark {
    @Param({"1000000", "2000000"})
    int howManyItems = 1000000;

    @Setup(Level.Trial)
    public void setUp() {
    }

    @TearDown(Level.Trial)
    public void tearDown() {
    }


    MergedField mergedField = MergedField.newMergedField().addField(Field.newField("f").build()).build();

    ResultPath path = ResultPath.rootPath().segment("f");
    ExecutionStepInfo rootStepInfo = newExecutionStepInfo()
            .path(path).type(Scalars.GraphQLString)
            .field(mergedField)
            .build();


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkDirectConstructorThroughput(Blackhole blackhole) {
        for (int i = 0; i < howManyItems; i++) {
            ResultPath newPath = path.segment(1);
            ExecutionStepInfo newOne = rootStepInfo.transform(Scalars.GraphQLInt, rootStepInfo, newPath);
            blackhole.consume(newOne);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkBuilderThroughput(Blackhole blackhole) {
        for (int i = 0; i < howManyItems; i++) {
            ResultPath newPath = path.segment(1);
            ExecutionStepInfo newOne = newExecutionStepInfo(rootStepInfo).parentInfo(rootStepInfo)
                    .type(Scalars.GraphQLInt).path(newPath).build();
            blackhole.consume(newOne);
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include("graphql.execution.ExecutionStepInfoBenchmark")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(opt).run();
    }

}
