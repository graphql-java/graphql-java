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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 5, time = 10)
@Fork(2)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CreateSchemaBenchmark {

    @Param({"large-schema-1.graphqls", "large-schema-2.graphqls", "large-schema-3.graphqls",
            "large-schema-4.graphqls", "large-schema-5.graphqls"})
    public String schemaFile;

    private String sdl;

    @Setup
    public void setup() {
        sdl = BenchmarkUtils.loadLargeSchema(schemaFile);
    }

    @Benchmark
    public GraphQLSchema benchmarkLargeSchemaCreateAvgTime() {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
    }

    @Benchmark
    public GraphQLSchema benchmarkLargeSchemaCreateAvgTimeFast() {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return new FastSchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
    }
}
