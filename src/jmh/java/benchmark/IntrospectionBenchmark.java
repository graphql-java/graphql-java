package benchmark;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class IntrospectionBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public ExecutionResult benchMarkIntrospectionAvgTime() {
        return graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public ExecutionResult benchMarkIntrospectionThroughput() {
        return graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    }

    private final GraphQL graphQL;


    public IntrospectionBenchmark() {
        String largeSchema = BenchmarkUtils.loadResource("large-schema-4.graphqls");
        GraphQLSchema graphQLSchema = SchemaGenerator.createdMockedSchema(largeSchema);
        graphQL = GraphQL.newGraphQL(graphQLSchema)
                .build();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include("benchmark.IntrospectionBenchmark")
                .build();

        new Runner(opt).run();
    }

}
