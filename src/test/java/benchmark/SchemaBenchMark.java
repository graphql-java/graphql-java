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

import java.util.concurrent.TimeUnit;

/**
 * This benchmarks schema creation
 * <p>
 * See https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/ for more samples
 * on what you can do with JMH
 * <p>
 * You MUST have the JMH plugin for IDEA in place for this to work :  https://github.com/artyushov/idea-jmh-plugin
 * <p>
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class SchemaBenchMark {

    static String largeSDL = BenchmarkUtils.loadResource("large-schema-3.graphqls");

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MINUTES)
    public void benchMarkLargeSchemaCreate(Blackhole blackhole) {
        blackhole.consume(createSchema(largeSDL));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkLargeSchemaCreateAvgTime(Blackhole blackhole) {
        blackhole.consume(createSchema(largeSDL));
    }

    private static GraphQLSchema createSchema(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);
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