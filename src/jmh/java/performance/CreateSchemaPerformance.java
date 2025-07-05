package performance;

import benchmark.BenchmarkUtils;
import graphql.schema.GraphQLSchema;
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
@Fork(3)
public class CreateSchemaPerformance {

    static String largeSDL = BenchmarkUtils.loadResource("large-schema-3.graphqls");
    static String extraLargeSDL = BenchmarkUtils.loadResource("extra-large-schema-1.graphqls");

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkLargeSchemaCreateAvgTime(Blackhole blackhole) {
        blackhole.consume(createSchema(largeSDL));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkExtraLargeSchemaCreateAvgTime(Blackhole blackhole) {
        blackhole.consume(createSchema(extraLargeSDL));
    }

    private static GraphQLSchema createSchema(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
    }
}