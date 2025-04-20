package benchmark;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.ExecutionStepInfo;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class SimpleQueryBenchmark {

    private static final int NUMBER_OF_FRIENDS = 10 * 100;
    private static final GraphQL GRAPHQL = buildGraphQL();

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public ExecutionResult benchMarkSimpleQueriesThroughput() {
        return executeQuery();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public ExecutionResult benchMarkSimpleQueriesAvgTime() {
        return executeQuery();
    }

    public static ExecutionResult executeQuery() {
        String query = "{ hero { name friends { name friends { name } } } }";
        return GRAPHQL.execute(query);
    }

    private static GraphQL buildGraphQL() {
        TypeDefinitionRegistry definitionRegistry = new SchemaParser().parse(BenchmarkUtils.loadResource("starWarsSchema.graphqls"));

        DataFetcher<CharacterDTO> heroDataFetcher = environment -> CharacterDTO.mkCharacter(environment, "r2d2", NUMBER_OF_FRIENDS);
        TypeResolver typeResolver = env -> env.getSchema().getObjectType("Human");

        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("QueryType").dataFetcher("hero", heroDataFetcher))
                .type(newTypeWiring("Character").typeResolver(typeResolver))
                .build();

        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(definitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema)
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

        public static CharacterDTO mkCharacter(DataFetchingEnvironment environment, String name, int friendCount) {
            Object sideEffect = environment.getArgument("episode");
            Assert.assertNull(sideEffect);
            ExecutionStepInfo anotherSideEffect = environment.getExecutionStepInfo();
            Assert.assertNotNull(anotherSideEffect);
            List<CharacterDTO> friends = new ArrayList<>(friendCount);
            for (int i = 0; i < friendCount; i++) {
                friends.add(mkCharacter(environment, "friend" + i, 0));
            }
            return new CharacterDTO(name, friends);
        }
    }
}
