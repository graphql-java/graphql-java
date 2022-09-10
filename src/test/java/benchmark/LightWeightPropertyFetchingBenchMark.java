package benchmark;

import com.google.common.collect.ImmutableList;
import graphql.GraphQL;
import graphql.execution.ExecutionStepInfo;
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
import java.util.Objects;
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
@Warmup(iterations = 1, time = 2, batchSize = 3)
@Measurement(iterations = 3, time = 5, batchSize = 3)
public class LightWeightPropertyFetchingBenchMark {


    private static final int NUMBER_OF_FRIENDS = 10 * 100;

    static GraphQL graphQL = buildGraphQL();


    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkHeavyWeightFetchingThroughput() {
        GraphQL.useLightWeightDataFetching(false);
        executeQuery();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkHeavyWeightFetchingAvgTime() {
        GraphQL.useLightWeightDataFetching(false);
        executeQuery();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkLightWeightFetchingThroughput() {
        GraphQL.useLightWeightDataFetching(true);
        executeQuery();
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkLightWeightFetchingAvgTime() {
        GraphQL.useLightWeightDataFetching(true);
        executeQuery();
    }

    public static void executeQuery() {
        String query = "{ hero { id name appearsIn friends { id name appearsIn friends { id name appearsIn } } } }";
        graphQL.execute(query);
    }

    private static final CharacterDTO R2D2 = CharacterDTO.mkCharacter(0, 20, "r2d2", NUMBER_OF_FRIENDS);


    @SuppressWarnings("unused")
    private static GraphQL buildGraphQL() {
        InputStream sdl = LightWeightPropertyFetchingBenchMark.class.getClassLoader().getResourceAsStream("starWarsSchema.graphqls");
        TypeDefinitionRegistry definitionRegistry = new SchemaParser().parse(new InputStreamReader(Objects.requireNonNull(sdl)));

        DataFetcher<?> heroDataFetcher = environment -> {
            Object sideEffect = environment.getArgument("episode");
            ExecutionStepInfo anotherSideEffect = environment.getExecutionStepInfo();

            return R2D2;
        };

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

        public String getId() {
            return "id=" + name;
        }

        public List<String> getAppearsIn() {
            return ImmutableList.of("NEWHOPE");
        }

        public String getName() {
            return name;
        }

        public List<CharacterDTO> getFriends() {
            return friends;
        }

        public static CharacterDTO mkCharacter(int depth, int maxDepth, String name, int friendCount) {
            if (depth > maxDepth) {
                return null;
            }
            List<CharacterDTO> friends = new ArrayList<>(friendCount);
            for (int i = 0; i < friendCount; i++) {
                friends.add(mkCharacter(depth + 1, maxDepth, "friend" + i, 0));
            }
            return new CharacterDTO(name, friends);
        }
    }
}
