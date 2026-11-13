package benchmark;

import graphql.schema.GraphQLSchema;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
public class CreateSchemaBenchmark {

    static String largeSDL = BenchmarkUtils.loadResource("large-schema-4.graphqls");

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MINUTES)
    public void benchmarkLargeSchemaCreate(Blackhole blackhole) {
        blackhole.consume(createSchema(largeSDL));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkLargeSchemaCreateAvgTime(Blackhole blackhole) {
        blackhole.consume(createSchema(largeSDL));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkLargeSchemaCreateAvgTimeFast(Blackhole blackhole) {
        blackhole.consume(createSchemaFast(largeSDL));
    }

    private static GraphQLSchema createSchema(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
    }

    private static GraphQLSchema createSchemaFast(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                registry,
                RuntimeWiring.MOCKED_WIRING);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    /// make this a main method if you want to run it in JProfiler etc..
    public static void mainXXX(String[] args) {
        int i = 0;
        while (true) {
            createSchema(largeSDL);
            i++;
            if (i % 100 == 0) {
                System.out.printf("%d\n", i);
            }
        }
    }
}
