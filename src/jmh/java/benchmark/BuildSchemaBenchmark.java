package benchmark;

import graphql.schema.idl.FastSchemaGenerator;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
@State(Scope.Benchmark)
public class BuildSchemaBenchmark {

    static String largeSDL = BenchmarkUtils.loadResource("large-schema-4.graphqls");

    private TypeDefinitionRegistry registry;

    @Setup
    public void setup() {
        // Parse SDL once before benchmarks run
        registry = new SchemaParser().parse(largeSDL);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkBuildSchemaAvgTime(Blackhole blackhole) {
        blackhole.consume(new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkBuildSchemaAvgTimeFast(Blackhole blackhole) {
        blackhole.consume(new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                registry,
                RuntimeWiring.MOCKED_WIRING));
    }
}
