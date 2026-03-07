package benchmark;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.introspection.IntrospectionWithDirectivesSupport;
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

/**
 * Benchmarks introspection with {@link IntrospectionWithDirectivesSupport} enabled,
 * using a query that fetches {@code appliedDirectives} on all introspection types.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(2)
public class IntrospectionWithDirectivesBenchmark {

    // Full introspection query that includes appliedDirectives on __Schema, __Type,
    // __Field, __EnumValue, and __InputValue — matching what IntrospectionWithDirectivesSupport adds.
    private static final String INTROSPECTION_WITH_DIRECTIVES_QUERY = "query IntrospectionWithDirectivesQuery {\n"
            + "  __schema {\n"
            + "    queryType { name }\n"
            + "    mutationType { name }\n"
            + "    subscriptionType { name }\n"
            + "    appliedDirectives { name args { name value } }\n"
            + "    types {\n"
            + "      ...FullType\n"
            + "    }\n"
            + "    directives {\n"
            + "      name\n"
            + "      description\n"
            + "      locations\n"
            + "      args(includeDeprecated: true) {\n"
            + "        ...InputValue\n"
            + "      }\n"
            + "      isRepeatable\n"
            + "    }\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "fragment FullType on __Type {\n"
            + "  kind\n"
            + "  name\n"
            + "  description\n"
            + "  isOneOf\n"
            + "  appliedDirectives { name args { name value } }\n"
            + "  fields(includeDeprecated: true) {\n"
            + "    name\n"
            + "    description\n"
            + "    appliedDirectives { name args { name value } }\n"
            + "    args(includeDeprecated: true) {\n"
            + "      ...InputValue\n"
            + "    }\n"
            + "    type {\n"
            + "      ...TypeRef\n"
            + "    }\n"
            + "    isDeprecated\n"
            + "    deprecationReason\n"
            + "  }\n"
            + "  inputFields(includeDeprecated: true) {\n"
            + "    ...InputValue\n"
            + "  }\n"
            + "  interfaces {\n"
            + "    ...TypeRef\n"
            + "  }\n"
            + "  enumValues(includeDeprecated: true) {\n"
            + "    name\n"
            + "    description\n"
            + "    appliedDirectives { name args { name value } }\n"
            + "    isDeprecated\n"
            + "    deprecationReason\n"
            + "  }\n"
            + "  possibleTypes {\n"
            + "    ...TypeRef\n"
            + "  }\n"
            + "}\n"
            + "\n"
            + "fragment InputValue on __InputValue {\n"
            + "  name\n"
            + "  description\n"
            + "  appliedDirectives { name args { name value } }\n"
            + "  type {\n"
            + "    ...TypeRef\n"
            + "  }\n"
            + "  defaultValue\n"
            + "  isDeprecated\n"
            + "  deprecationReason\n"
            + "}\n"
            + "\n"
            + "fragment TypeRef on __Type {\n"
            + "  kind\n"
            + "  name\n"
            + "  ofType {\n"
            + "    kind\n"
            + "    name\n"
            + "    ofType {\n"
            + "      kind\n"
            + "      name\n"
            + "      ofType {\n"
            + "        kind\n"
            + "        name\n"
            + "        ofType {\n"
            + "          kind\n"
            + "          name\n"
            + "          ofType {\n"
            + "            kind\n"
            + "            name\n"
            + "            ofType {\n"
            + "              kind\n"
            + "              name\n"
            + "              ofType {\n"
            + "                kind\n"
            + "                name\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}\n";

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
        graphQLSchema = new IntrospectionWithDirectivesSupport().apply(graphQLSchema);
        graphQL = GraphQL.newGraphQL(graphQLSchema).build();
    }

    private static String loadSchema(String schemaFile) {
        if (schemaFile.equals("large-schema-5.graphqls")) {
            return BenchmarkUtils.loadResource("large-schema-5.graphqls.part1")
                    + BenchmarkUtils.loadResource("large-schema-5.graphqls.part2");
        }
        return BenchmarkUtils.loadResource(schemaFile);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public ExecutionResult benchMarkIntrospectionWithDirectivesAvgTime() {
        return graphQL.execute(INTROSPECTION_WITH_DIRECTIVES_QUERY);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public ExecutionResult benchMarkIntrospectionWithDirectivesThroughput() {
        return graphQL.execute(INTROSPECTION_WITH_DIRECTIVES_QUERY);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include("benchmark.IntrospectionWithDirectivesBenchmark")
                .build();

        new Runner(opt).run();
    }

}
