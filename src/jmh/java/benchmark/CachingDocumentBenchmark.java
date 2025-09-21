package benchmark;

import graphql.GraphQL;
import graphql.StarWarsSchema;
import graphql.execution.preparsed.NoOpPreparsedDocumentProvider;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.execution.preparsed.caching.CachingDocumentProvider;
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
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
public class CachingDocumentBenchmark {

    @Param({"50", "500", "5000"})
    public int querySize;

    @Param({"10", "50", "500"})
    public int queryCount;

    @Setup(Level.Trial)
    public void setUp() {
    }

    private static final GraphQL GRAPHQL_CACHING_ON = buildGraphQL(true);
    private static final GraphQL GRAPHQL_CACHING_OFF = buildGraphQL(true);

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkCachingOnAvgTime() {
        executeQuery(true);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkCachingOffAvgTime() {
        executeQuery(false);
    }

    public void executeQuery(boolean cachingOn) {
        String query = buildQuery(querySize);
        GraphQL graphQL = cachingOn ? GRAPHQL_CACHING_ON : GRAPHQL_CACHING_OFF;

        for (int i = 0; i < queryCount; i++) {
            graphQL.execute(query);
        }
    }

    private static String buildQuery(int howManyAliases) {
        StringBuilder query = new StringBuilder("query q { hero { \n");
        for (int i = 0; i < howManyAliases; i++) {
            query.append("nameAlias").append(i).append(" : name\n");
        }
        query.append("}}");
        return query.toString();
    }

    private static GraphQL buildGraphQL(boolean cachingOn) {
        PreparsedDocumentProvider documentProvider = NoOpPreparsedDocumentProvider.INSTANCE;
        if (cachingOn) {
            documentProvider = new CachingDocumentProvider();
        }
        return GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(documentProvider)
                .build();
    }
}
