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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Isolates the total cost of {@code PerLevelDataLoaderDispatchStrategy} bookkeeping.
 * <p>
 * The same wide query runs with identical data fetchers (none of them use a DataLoader) in two
 * modes: {@code none} executes without a {@code DataLoaderRegistry}, so the engine uses the no-op
 * dispatch strategy; {@code perLevel} passes a registry holding one unused loader, which switches
 * the engine to {@code PerLevelDataLoaderDispatchStrategy}. The difference between the two scores
 * is everything the strategy costs for this query shape.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(2)
public class PerLevelDispatchOverheadPerformance {

    private static final String SDL = ""
            + "type Query { items(count: Int!): [Item!]! }\n"
            + "type Item { id: ID! a: String b: String c: String d: String e: String child: Child }\n"
            + "type Child { id: ID! x: String y: String grand: [Grand!]! }\n"
            + "type Grand { v: String w: String }\n";

    private static final int GRANDCHILDREN_PER_CHILD = 5;

    @Param({"250"})
    public int count;

    @Param({"false", "true"})
    public boolean async;

    @Param({"none", "perLevel"})
    public String strategy;

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
            return Map.of("id", "C-" + item.get("id"), "x", "x", "y", "y");
        };
        DataFetcher<Object> grandDf = env -> {
            List<Map<String, Object>> grand = new ArrayList<>(GRANDCHILDREN_PER_CHILD);
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

        Map<String, List<?>> data = execute().getData();
        Assert.assertTrue(data.get("items").size() == count, () -> "items: " + data);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool.shutdownNow();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public ExecutionResult executeWideQuery() {
        return execute();
    }

    private ExecutionResult execute() {
        ExecutionInput.Builder input = ExecutionInput.newExecutionInput().query(query);
        if (strategy.equals("perLevel")) {
            input.dataLoaderRegistry(DataLoaderRegistry.newRegistry()
                    .register("unused", DataLoaderFactory.newDataLoader(keys -> CompletableFuture.completedFuture(keys)))
                    .build());
        }
        ExecutionResult result = graphQL.execute(input.build());
        Assert.assertTrue(result.getErrors().isEmpty(), () -> "errors: " + result.getErrors());
        return result;
    }
}
