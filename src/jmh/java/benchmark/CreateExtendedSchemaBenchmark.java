package benchmark;

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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static benchmark.BenchmarkUtils.runInToolingForSomeTimeThenExit;

/**
 * This JMH
 */
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class CreateExtendedSchemaBenchmark {

    private static final String SDL = mkSDL();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MINUTES)
    public void benchmarkLargeSchemaCreate(Blackhole blackhole) {
        blackhole.consume(createSchema(SDL));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkLargeSchemaCreateAvgTime(Blackhole blackhole) {
        blackhole.consume(createSchema(SDL));
    }

    private static GraphQLSchema createSchema(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
    }

    /* something like
    type Query { q : String } interface I { f : String }
    interface I1 implements I {
        f : String
        f1 : String
    }
    type O1_1 implements I1 & I {
        f : String
        f1 : String
    }
    type O1_2 implements I1 & I {
        f : String
        f1 : String
    }
     */
    private static String mkSDL() {
        int numTypes = 10000;
        int numExtends = 10;

        StringBuilder sb = new StringBuilder();
        sb.append("type Query { q : String } interface I { f : String } interface X { x : String }\n");
        for (int i = 0; i < numTypes; i++) {
            sb.append("interface I").append(i).append(" implements I { \n")
                    .append("\tf : String \n")
                    .append("\tf").append(i).append(" : String \n").append("}\n");

            sb.append("type O").append(i).append(" implements I").append(i).append(" & I { \n")
                    .append("\tf : String \n")
                    .append("\tf").append(i).append(" : String \n")
                    .append("}\n");

            sb.append("extend type O").append(i).append(" implements X").append(" { \n")
                    .append("\tx : String \n")
                    .append("}\n");

            for (int j = 0; j < numExtends; j++) {
                sb.append("extend type O").append(i).append(" { \n")
                        .append("\textendedF").append(j).append(" : String \n")
                        .append("}\n");

            }
        }
        return sb.toString();
    }

    public static void main(String[] args) throws RunnerException {
        try {
            runAtStartup();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        Options opt = new OptionsBuilder()
                .include("benchmark.CreateExtendedSchemaBenchmark")
                .build();

        new Runner(opt).run();
    }

    private static void runAtStartup() {
        runInToolingForSomeTimeThenExit(
                () -> {
                },
                () -> createSchema(SDL),
                () -> {
                }
        );
    }
}