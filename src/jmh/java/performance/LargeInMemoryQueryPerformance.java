package performance;

import benchmark.BenchmarkUtils;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * This benchmark is an attempt to have a large in memory query that involves only sync work but lots of
 * data fetching invocation
 * <p>
 * It can also be run in a forever mode say if you want to connect a profiler to it say
 */
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 2)
@Fork(2)
public class LargeInMemoryQueryPerformance {

    GraphQL graphQL;
    volatile boolean shutDown;

    @Setup(Level.Trial)
    public void setUp() {
        shutDown = false;
        graphQL = buildGraphQL();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        shutDown = true;
    }


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Object benchMarkSimpleQueriesThroughput() {
        return runManyQueriesToCompletion();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Object benchMarkSimpleQueriesAvgTime() {
        return runManyQueriesToCompletion();
    }


    public static void main(String[] args) throws Exception {
        // just to make sure it's all valid before testing
        runAtStartup();

        Options opt = new OptionsBuilder()
                .include("performance.LargeInMemoryQueryBenchmark")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(opt).run();
    }

    private static void runAtStartup() {

        LargeInMemoryQueryPerformance complexQueryBenchmark = new LargeInMemoryQueryPerformance();
        BenchmarkUtils.runInToolingForSomeTimeThenExit(
                complexQueryBenchmark::setUp,
                complexQueryBenchmark::runManyQueriesToCompletion,
                complexQueryBenchmark::tearDown

        );
    }


    private Object runManyQueriesToCompletion() {
        return graphQL.execute(
                "query {\n" +
                        "\n" +
                        "   giveMeLargeResponse {\n" +
                        "      someValue\n" +
                        "   }\n" +
                        "}"
        );
    }

    private static final List<SomeWrapper> manyObjects = IntStream
            .range(0, 10_000_000)
            .mapToObj(i -> new SomeWrapper("value #" + i))
            .collect(Collectors.toList());

    public static class SomeWrapper {
        String someValue;

        public SomeWrapper(String someValue) {
            this.someValue = someValue;
        }
    }

    private GraphQL buildGraphQL() {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse("\n" +
                        "type Query {\n" +
                        "   giveMeLargeResponse: [SomeWrapper]\n" +
                        "}\n" +
                        "type SomeWrapper {\n" +
                        "   someValue: String\n" +
                        "}\n"
        );
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("giveMeLargeResponse", env -> manyObjects))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }
}
