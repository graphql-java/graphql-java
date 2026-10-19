package performance;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Measures the per-field bookkeeping of {@code PerLevelDataLoaderDispatchStrategy}, which is the
 * strategy graphql-java uses whenever a {@code DataLoaderRegistry} is present.
 * <p>
 * The query is wide rather than deep: {@code count} list items, each with scalar fields, a
 * DataLoader-backed child object and a list of grandchildren, so the strategy's
 * {@code executeObject} / {@code onCompletionFinished} / readiness checks run thousands of times
 * per request while the data fetchers themselves do almost nothing.
 * <p>
 * With {@code async = true} a scalar field on every item and the child batch loader complete on a
 * small thread pool, so object completions for the same level race on the strategy's per-level
 * state from several threads, as they do in a server whose fetchers resume on worker threads.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(2)
public class PerLevelDispatchPerformance {

    private static final String SDL = ""
            + "type Query { items(count: Int!): [Item!]! }\n"
            + "type Item { id: ID! a: String b: String c: String d: String e: String child: Child }\n"
            + "type Child { id: ID! x: String y: String grand: [Grand!]! }\n"
            + "type Grand { v: String w: String }\n";

    private static final int GRANDCHILDREN_PER_CHILD = 5;

    @Param({"50", "250"})
    public int count;

    @Param({"false", "true"})
    public boolean async;

    private GraphQL graphQL;
    private String query;
    private ExecutorService pool;

    @Setup(Level.Trial)
    public void setup() {
        pool = Executors.newFixedThreadPool(4);

        DataFetcher<Object> itemsDf = env -> {
            int n = env.getArgument("count");
            List<Map<String, Object>> items = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "I-" + i);
                item.put("a", "a" + i);
                item.put("b", "b" + i);
                item.put("c", "c" + i);
                item.put("d", "d" + i);
                item.put("e", "e" + i);
                items.add(item);
            }
            return items;
        };
        DataFetcher<Object> asyncScalarDf = env -> {
            Map<String, Object> item = env.getSource();
            Object value = item.get("a");
            return async ? CompletableFuture.supplyAsync(() -> value, pool) : value;
        };
        DataFetcher<Object> childDf = env -> {
            Map<String, Object> item = env.getSource();
            DataLoader<String, Map<String, Object>> loader = env.getDataLoader("child");
            return loader.load((String) item.get("id"));
        };
        DataFetcher<Object> grandDf = env -> {
            List<Map<String, Object>> grand = new ArrayList<>();
            for (int i = 0; i < GRANDCHILDREN_PER_CHILD; i++) {
                grand.add(Map.of("v", "v" + i, "w", "w" + i));
            }
            return grand;
        };

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", b -> b.dataFetcher("items", itemsDf))
                .type("Item", b -> b.dataFetcher("a", asyncScalarDf).dataFetcher("child", childDf))
                .type("Child", b -> b.dataFetcher("grand", grandDf))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(SDL), wiring);
        graphQL = GraphQL.newGraphQL(schema).build();
        query = "{ items(count: " + count + ") { id a b c d e child { id x y grand { v w } } } }";

        Map<String, List<Map<String, Map<String, List<?>>>>> data = execute().getData();
        List<Map<String, Map<String, List<?>>>> items = data.get("items");
        Assert.assertTrue(items.size() == count, () -> "items: " + items.size());
        Assert.assertTrue(items.get(count - 1).get("child").get("grand").size() == GRANDCHILDREN_PER_CHILD,
                () -> "grandchildren missing: " + items.get(count - 1));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool.shutdownNow();
    }

    private BatchLoader<String, Map<String, Object>> childBatchLoader() {
        return keys -> {
            List<Map<String, Object>> children = new ArrayList<>(keys.size());
            for (String key : keys) {
                children.add(Map.of("id", "C-" + key, "x", "x", "y", "y"));
            }
            return async ? CompletableFuture.supplyAsync(() -> children, pool) : CompletableFuture.completedFuture(children);
        };
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void executeWideQuery(Blackhole blackhole) {
        blackhole.consume(execute());
    }

    private ExecutionResult execute() {
        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry()
                .register("child", DataLoaderFactory.newDataLoader(childBatchLoader()))
                .build();
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(registry)
                .build();
        ExecutionResult result = graphQL.execute(input);
        Assert.assertTrue(result.getErrors().isEmpty(), () -> "errors: " + result.getErrors());
        return result;
    }
}
