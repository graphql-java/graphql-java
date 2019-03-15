package benchmark;

import graphql.GraphQL;
import graphql.execution.instrumentation.tracing.TracingInstrumentation;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * See http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/ for more samples
 * on what you can do with JMH
 *
 * You MUST have the JMH plugin for IDEA in place for this to work :  https://github.com/artyushov/idea-jmh-plugin
 *
 * Install it and then just hit "Run" on a certain benchmark method
 */
@Warmup(iterations = 2, time = 5, batchSize = 3)
@Measurement(iterations = 3, time = 10, batchSize = 4)
public class BenchMark {

    private static final int NUMBER_OF_FRIENDS = 10 * 100;

    static GraphQL graphQL = buildGraphQL();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkSimpleQueriesThroughput() {
        executeQuery();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkSimpleQueriesAvgTime() {
        executeQuery();
    }

    public static void executeQuery() {
        String query = "{ hero { name friends { name friends { name } } } }";
        graphQL.execute(query);
    }

    private static GraphQL buildGraphQL() {
        InputStream sdl = BenchMark.class.getClassLoader().getResourceAsStream("starWarsSchema.graphqls");
        TypeDefinitionRegistry definitionRegistry = new SchemaParser().parse(new InputStreamReader(sdl));

        DataFetcher heroDataFetcher = environment -> CharacterDTO.mkCharacter("r2d2", NUMBER_OF_FRIENDS);

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(
                        newTypeWiring("QueryType").dataFetcher("hero", heroDataFetcher))
                .type(newTypeWiring("Character").typeResolver(
                        env -> env.getSchema().getObjectType("Human")
                ))
                .build();
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(definitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema)
                .instrumentation(new TracingInstrumentation())
                .build();
    }

    static class CharacterDTO {
        private final String name;
        private final List<CharacterDTO> friends;

        CharacterDTO(String name, List<CharacterDTO> friends) {
            this.name = name;
            this.friends = friends;
        }

        public String getName() {
            return name;
        }

        public List<CharacterDTO> getFriends() {
            return friends;
        }

        public static CharacterDTO mkCharacter(String name, int friendCount) {
            List<CharacterDTO> friends = new ArrayList<>(friendCount);
            for (int i = 0; i < friendCount; i++) {
                friends.add(mkCharacter("friend" + i, 0));
            }
            return new CharacterDTO(name, friends);
        }
    }
}
