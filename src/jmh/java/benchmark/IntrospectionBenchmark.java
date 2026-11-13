package benchmark;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
public class IntrospectionBenchmark {

    @Param({
            "large-schema-2.graphqls",
            "large-schema-3.graphqls",
            "large-schema-4.graphqls",
            "large-schema-5.graphqls",
            "large-schema-federated-1.graphqls"
    })
    String schemaFile;

    private GraphQL graphQL;

    @Setup(Level.Trial)
    public void setup() {
        String schema = loadSchema(schemaFile);
        GraphQLSchema graphQLSchema = SchemaGenerator.createdMockedSchema(schema);
        graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    private static String loadSchema(String schemaFile) {
        if (schemaFile.equals("large-schema-5.graphqls")) {
            // This schema is split across two files due to its size (11.3 MB)
            return BenchmarkUtils.loadResource("large-schema-5.graphqls.part1")
                    + BenchmarkUtils.loadResource("large-schema-5.graphqls.part2");
        }
        return BenchmarkUtils.loadResource(schemaFile);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public ExecutionResult benchMarkIntrospectionAvgTime() {
        return graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public ExecutionResult benchMarkIntrospectionThroughput() {
        return graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include("benchmark.IntrospectionBenchmark")
                .build();

        new Runner(opt).run();
    }

}
