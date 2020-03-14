package benchmark;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import static graphql.schema.FieldCoordinates.coordinates;

@State(Scope.Benchmark)
public class FetchDataFetcherFromCodeRegistryBenchMark {

    DataFetcher<?> df = env -> env;
    private GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
            .dataFetcher(coordinates("Query", "f1"), df)
            .dataFetcher(coordinates("Query", "f2"), df)
            .dataFetcher(coordinates("Query", "f3"), df)
            .build();

    private GraphQLFieldDefinition nameField = GraphQLFieldDefinition.newFieldDefinition().name("f").type(Scalars.GraphQLString).build();


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 1, batchSize = 500)
    @Measurement(iterations = 2, batchSize = 500)
    public void benchMarkGetDF() {
        for (int i = 0; i < 3; i++) {
            codeRegistry.getDataFetcher(coordinates("Query", "f" + i), nameField);
        }
    }
}
