package benchmark;

import graphql.GraphQL;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * See <a href="https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/">...</a> for more samples
 * on what you can do with JMH
 * <p>
 * You MUST have the JMH plugin for IDEA in place for this to work :  <a href="https://github.com/artyushov/idea-jmh-plugin">...</a>
 * <p>
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 5, batchSize = 3)
@Measurement(iterations = 3, time = 10, batchSize = 4)
public class StopQueryBenchMark {

    public static final String LONG_LIST = "query q { longList { name age active } }";
    public static final String STOP_LIST = "query q { stopList { name age active } }";

    static GraphQL graphQL = buildGraphQL();
    static List<Object> list = mkList(1000);

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkLongListThroughput() {
        executeQuery(LONG_LIST);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkLongListAvgTime() {
        executeQuery(LONG_LIST);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkStopListThroughput() {
        executeQuery(STOP_LIST);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkStopListAvgTime() {
        executeQuery(STOP_LIST);
    }

    public static void executeQuery(String query) {
        graphQL.execute(query);
    }

    private static GraphQL buildGraphQL() {
        InputStream sdl = StopQueryBenchMark.class.getClassLoader().getResourceAsStream("large-list-schema.graphqls");
        TypeDefinitionRegistry definitionRegistry = new SchemaParser().parse(new InputStreamReader(sdl));

        DataFetcher<?> longListDF = env -> DataFetcherResult.newResult().data(list).build();
        DataFetcher<?> stopListDF = env -> DataFetcherResult.newResult().data(list).stopPathExecution(true).build();

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(
                        newTypeWiring("Query")
                                .dataFetcher("longList", longListDF)
                                .dataFetcher("stopList", stopListDF)
                )
                .build();
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(definitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema)
                .build();
    }

    private static List<Object> mkList(int howMany) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < howMany; i++) {
            Map<String, Object> obj = new HashMap<>();
            obj.put("name", "Name" + i);
            obj.put("age", i);
            obj.put("active", i % 2 == 0);
            list.add(obj);
        }
        return list;
    }

}
