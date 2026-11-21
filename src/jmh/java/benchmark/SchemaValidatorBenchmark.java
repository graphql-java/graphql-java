package benchmark;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.FastSchemaGenerator;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.validation.SchemaValidator;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class SchemaValidatorBenchmark {

    @Param({"large-schema-4.graphqls", "large-schema-federated-1.graphqls"})
    public String schemaFile;

    private GraphQLSchema schema;

    @Setup
    public void setup() {
        String sdl = BenchmarkUtils.loadResource(schemaFile);
        schema = new FastSchemaGenerator().makeExecutableSchema(
                SchemaGenerator.Options.defaultOptions().withValidation(false),
                new SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING);
    }

    @Benchmark
    public void validateSchema(Blackhole blackhole) {
        blackhole.consume(new SchemaValidator().validateSchema(schema));
    }
}
