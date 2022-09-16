package benchmark;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.Backdoor;
import graphql.introspection.IntrospectionQuery;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import static benchmark.BenchmarkUtils.loadResource;

@State(Scope.Benchmark)
@Warmup(iterations = 2)
@Measurement(iterations = 3)

public class NoInstrumentationBenchmark {

    private final GraphQL graphQL;

    public NoInstrumentationBenchmark() {
        String largeSchema = loadResource("large-schema-4.graphqls");
        GraphQLSchema graphQLSchema = SchemaGenerator.createdMockedSchema(largeSchema);
        graphQL = GraphQL.newGraphQL(graphQLSchema)
                //.instrumentation(countingInstrumentation)
                .build();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public ExecutionResult benchMarkNormalIntrospection() {
        Backdoor.setUseInstrumentation(true);
        return graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public ExecutionResult benchMarkNoInstrumentationIntrospection() {
        Backdoor.setUseInstrumentation(false);
        return graphQL.execute(IntrospectionQuery.INTROSPECTION_QUERY);
    }
}
