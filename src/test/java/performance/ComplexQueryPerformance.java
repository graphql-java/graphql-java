package performance;

import benchmark.BenchmarkUtils;
import com.google.common.collect.ImmutableList;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
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
import org.openjdk.jmh.annotations.Param;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * This benchmark is an attempt to have a more complex query that involves async and sync work together
 * along with multiple threads happening.
 * <p>
 * It can also be run in a forever mode say if you want to connect a profiler to it say
 */
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 2)
@Fork(2)
public class ComplexQueryPerformance {

    @Param({"5", "10", "20"})
    int howManyItems = 5;
    int howLongToSleep = 5;
    int howManyQueries = 10;
    int howManyQueryThreads = 10;
    int howManyFetcherThreads = 10;

    ExecutorService queryExecutorService;
    ExecutorService fetchersExecutorService;
    GraphQL graphQL;
    volatile boolean shutDown;

    @Setup(Level.Trial)
    public void setUp() {
        shutDown = false;
        queryExecutorService = Executors.newFixedThreadPool(howManyQueryThreads);
        fetchersExecutorService = Executors.newFixedThreadPool(howManyFetcherThreads);
        graphQL = buildGraphQL();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        shutDown = true;
        queryExecutorService.shutdownNow();
        fetchersExecutorService.shutdownNow();
    }


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Object benchMarkSimpleQueriesThroughput() {
        return runManyQueriesToCompletion();
    }


    public static void main(String[] args) throws Exception {
        // just to make sure it's all valid before testing
        runAtStartup();

        Options opt = new OptionsBuilder()
                .include("benchmark.ComplexQueryBenchmark")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(opt).run();
    }

    @SuppressWarnings({"ConstantValue", "LoopConditionNotUpdatedInsideLoop"})
    private static void runAtStartup() {

        ComplexQueryPerformance complexQueryBenchmark = new ComplexQueryPerformance();
        complexQueryBenchmark.howManyQueries = 5;
        complexQueryBenchmark.howManyItems = 10;

        BenchmarkUtils.runInToolingForSomeTimeThenExit(
                complexQueryBenchmark::setUp,
                complexQueryBenchmark::runManyQueriesToCompletion,
                complexQueryBenchmark::tearDown

        );
    }


    @SuppressWarnings("UnnecessaryLocalVariable")
    private Void runManyQueriesToCompletion() {
        CompletableFuture<?>[] cfs = new CompletableFuture[howManyQueries];
        for (int i = 0; i < howManyQueries; i++) {
            cfs[i] = CompletableFuture.supplyAsync(() -> executeQuery(howManyItems, howLongToSleep), queryExecutorService).thenCompose(cf -> cf);
        }
        Void result = CompletableFuture.allOf(cfs).join();
        return result;
    }

    public CompletableFuture<ExecutionResult> executeQuery(int howMany, int howLong) {
        String fields = "id name f1 f2 f3 f4 f5 f6 f7 f8 f9 f10";
        String query = "query q {"
                + String.format("shops(howMany : %d) { %s departments( howMany : %d) { %s products(howMany : %d) { %s }}}\n"
                , howMany, fields, 10, fields, 5, fields)
                + String.format("expensiveShops(howMany : %d howLong : %d) { %s expensiveDepartments( howMany : %d howLong : %d) { %s expensiveProducts(howMany : %d howLong : %d) { %s }}}\n"
                , howMany, howLong, fields, 10, howLong, fields, 5, howLong, fields)
                + "}";
        return graphQL.executeAsync(ExecutionInput.newExecutionInput(query).build());
    }

    private GraphQL buildGraphQL() {
        TypeDefinitionRegistry definitionRegistry = new SchemaParser().parse(PerformanceTestingUtils.loadResource("storesanddepartments.graphqls"));

        DataFetcher<?> shopsDF = env -> mkHowManyThings(env.getArgument("howMany"));
        DataFetcher<?> expensiveShopsDF = env -> supplyAsync(() -> sleepAndReturnThings(env));
        DataFetcher<?> departmentsDF = env -> mkHowManyThings(env.getArgument("howMany"));
        DataFetcher<?> expensiveDepartmentsDF = env -> supplyAsyncListItems(env, () -> sleepAndReturnThings(env));
        DataFetcher<?> productsDF = env -> mkHowManyThings(env.getArgument("howMany"));
        DataFetcher<?> expensiveProductsDF = env -> supplyAsyncListItems(env, () -> sleepAndReturnThings(env));

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("shops", shopsDF)
                        .dataFetcher("expensiveShops", expensiveShopsDF))
                .type(newTypeWiring("Shop")
                        .dataFetcher("departments", departmentsDF)
                        .dataFetcher("expensiveDepartments", expensiveDepartmentsDF))
                .type(newTypeWiring("Department")
                        .dataFetcher("products", productsDF)
                        .dataFetcher("expensiveProducts", expensiveProductsDF))
                .build();

        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(definitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }

    private <T> CompletableFuture<T> supplyAsyncListItems(DataFetchingEnvironment environment, Supplier<T> codeToRun) {
        return supplyAsync(codeToRun);
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> codeToRun) {
        if (!shutDown) {
            //logEvery(100, "async fetcher");
            return CompletableFuture.supplyAsync(codeToRun, fetchersExecutorService);
        } else {
            // if we have shutdown - get on with it, so we shut down quicker
            return CompletableFuture.completedFuture(codeToRun.get());
        }
    }

    private List<IdAndNamedThing> sleepAndReturnThings(DataFetchingEnvironment env) {
        // by sleeping, we hope to cause the objects to stay longer in GC land and hence have a longer lifecycle
        // then a simple stack say or young gen gc.  I don't know this will work, but I am trying it
        // to represent work that takes some tie to complete
        sleep(env.getArgument("howLong"));
        return mkHowManyThings(env.getArgument("howMany"));
    }

    private void sleep(Integer howLong) {
        if (howLong > 0) {
            try {
                Thread.sleep(howLong);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    AtomicInteger logCount = new AtomicInteger();

    private void logEvery(int every, String s) {
        int count = logCount.getAndIncrement();
        if (count == 0 || count % every == 0) {
            System.out.println("\t" + count + "\t" + s);
        }
    }

    private List<IdAndNamedThing> mkHowManyThings(Integer howMany) {
        ImmutableList.Builder<IdAndNamedThing> builder = ImmutableList.builder();
        for (int i = 0; i < howMany; i++) {
            builder.add(new IdAndNamedThing(i));
        }
        return builder.build();
    }

    @SuppressWarnings("unused")
    static class IdAndNamedThing {
        private final int i;

        public IdAndNamedThing(int i) {
            this.i = i;
        }

        public String getId() {
            return "id" + i;
        }

        public String getName() {
            return "name" + i;
        }

        public String getF1() {
            return "f1" + i;
        }

        public String getF2() {
            return "f2" + i;
        }

        public String getF3() {
            return "f3" + i;
        }

        public String getF4() {
            return "f4" + i;
        }

        public String getF5() {
            return "f5" + i;
        }

        public String getF6() {
            return "f6" + i;
        }

        public String getF7() {
            return "f7" + i;
        }

        public String getF8() {
            return "f8" + i;
        }

        public String getF9() {
            return "f9" + i;
        }

        public String getF10() {
            return "f10" + i;
        }
    }
}
