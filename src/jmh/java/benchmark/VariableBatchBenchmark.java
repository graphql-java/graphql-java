package benchmark;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.VariableBatchExecutionResult;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Compares one variable batch with repeated ordinary executions sharing the same registry. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class VariableBatchBenchmark {
    private static final String QUERY = "query($id:Int!) { a:value(id:$id) b:value(id:$id) c:value(id:$id) }";

    @Param({"16"})
    public int size;

    @Param({"false", "true"})
    public boolean cachedDocument;

    @Param({"false", "true"})
    public boolean dataLoader;

    private GraphQL graphQL;
    private List<Map<String, Object>> variables;

    @Setup
    public void setup() {
        DataFetcher<?> fetcher = environment -> dataLoader
                ? environment.getDataLoader("values").load(environment.getArgument("id"))
                : environment.getArgument("id");
        var schema = new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse("type Query { value(id:Int!):Int! }"),
                RuntimeWiring.newRuntimeWiring().type("Query", type -> type.dataFetcher("value", fetcher)).build());
        GraphQL.Builder builder = GraphQL.newGraphQL(schema);
        if (cachedDocument) {
            AtomicReference<PreparsedDocumentEntry> prepared = new AtomicReference<>();
            builder.preparsedDocumentProvider((input, compute) -> CompletableFuture.completedFuture(
                    prepared.updateAndGet(value -> value == null ? compute.apply(input) : value)));
        }
        graphQL = builder.build();
        variables = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            variables.add(Map.of("id", index % 4));
        }
    }

    @Benchmark
    public List<ExecutionResult> variableBatch() {
        ExecutionInput input = ExecutionInput.newExecutionInput(QUERY)
                .variables(variables).dataLoaderRegistry(registry()).build();
        VariableBatchExecutionResult result = (VariableBatchExecutionResult) graphQL.execute(input);
        return result.getResultsAsync().join();
    }

    @Benchmark
    public List<ExecutionResult> separateExecutions() {
        DataLoaderRegistry registry = registry();
        List<CompletableFuture<ExecutionResult>> results = new ArrayList<>();
        for (Map<String, Object> values : variables) {
            results.add(graphQL.executeAsync(ExecutionInput.newExecutionInput(QUERY)
                    .variables(values).dataLoaderRegistry(registry).build()));
        }
        CompletableFuture.allOf(results.toArray(new CompletableFuture<?>[0])).join();
        List<ExecutionResult> completed = new ArrayList<>();
        results.forEach(result -> completed.add(result.join()));
        return completed;
    }

    private DataLoaderRegistry registry() {
        DataLoaderRegistry registry = new DataLoaderRegistry();
        if (dataLoader) {
            BatchLoader<Object, Object> loader = keys -> CompletableFuture.completedFuture(keys);
            registry.register("values", DataLoaderFactory.newDataLoader(loader));
        }
        return registry;
    }
}
