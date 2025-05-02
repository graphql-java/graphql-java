package performance;

import graphql.Assert;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderRegistry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3)
@Fork(3)
public class DataLoaderPerformance {

    static Owner o1 = new Owner("O-1", "Andi", List.of("P-1", "P-2", "P-3"));
    static Owner o2 = new Owner("O-2", "George", List.of("P-4", "P-5", "P-6"));
    static Owner o3 = new Owner("O-3", "Peppa", List.of("P-7", "P-8", "P-9", "P-10"));

    static Pet p1 = new Pet("P-1", "Bella", "O-1", List.of("P-2", "P-3", "P-4"));
    static Pet p2 = new Pet("P-2", "Charlie", "O-2", List.of("P-1", "P-5", "P-6"));
    static Pet p3 = new Pet("P-3", "Luna", "O-3", List.of("P-1", "P-2", "P-7", "P-8"));
    static Pet p4 = new Pet("P-4", "Max", "O-1", List.of("P-1", "P-9", "P-10"));
    static Pet p5 = new Pet("P-5", "Lucy", "O-2", List.of("P-2", "P-6"));
    static Pet p6 = new Pet("P-6", "Cooper", "O-3", List.of("P-3", "P-5", "P-7"));
    static Pet p7 = new Pet("P-7", "Daisy", "O-1", List.of("P-4", "P-6", "P-8"));
    static Pet p8 = new Pet("P-8", "Milo", "O-2", List.of("P-3", "P-7", "P-9"));
    static Pet p9 = new Pet("P-9", "Lola", "O-3", List.of("P-4", "P-8", "P-10"));
    static Pet p10 = new Pet("P-10", "Rocky", "O-1", List.of("P-4", "P-9"));

    static Map<String, Owner> owners = Map.of(
            o1.id, o1,
            o2.id, o2,
            o3.id, o3
    );
    static Map<String, Pet> pets = Map.of(
            p1.id, p1,
            p2.id, p2,
            p3.id, p3,
            p4.id, p4,
            p5.id, p5,
            p6.id, p6,
            p7.id, p7,
            p8.id, p8,
            p9.id, p9,
            p10.id, p10
    );

    static class Owner {
        public Owner(String id, String name, List<String> petIds) {
            this.id = id;
            this.name = name;
            this.petIds = petIds;
        }

        String id;
        String name;
        List<String> petIds;
    }

    static class Pet {
        public Pet(String id, String name, String ownerId, List<String> friendsIds) {
            this.id = id;
            this.name = name;
            this.ownerId = ownerId;
            this.friendsIds = friendsIds;
        }

        String id;
        String name;
        String ownerId;
        List<String> friendsIds;
    }


    static BatchLoader<String, Owner> ownerBatchLoader = list -> {
        List<Owner> collect = list.stream().map(key -> {
            Owner owner = owners.get(key);
            return owner;
        }).collect(Collectors.toList());
        return CompletableFuture.completedFuture(collect);
    };
    static BatchLoader<String, Pet> petBatchLoader = list -> {
        List<Pet> collect = list.stream().map(key -> {
            Pet owner = pets.get(key);
            return owner;
        }).collect(Collectors.toList());
        return CompletableFuture.completedFuture(collect);
    };

    static final String ownerDLName = "ownerDL";
    static final String petDLName = "petDL";

    @State(Scope.Benchmark)
    public static class MyState {

        GraphQLSchema schema;
        GraphQL graphQL;
        private String query;

        @Setup
        public void setup() {
            try {
                String sdl = PerformanceTestingUtils.loadResource("dataLoaderPerformanceSchema.graphqls");


                DataLoaderRegistry registry = new DataLoaderRegistry();

                DataFetcher ownersDF = (env -> {
                    return env.getDataLoader(ownerDLName).loadMany(List.of("O-1", "0-2", "O-3"));
                });
                DataFetcher petsDf = (env -> {
                    Owner owner = env.getSource();
                    return env.getDataLoader(petDLName).loadMany((List) owner.petIds);
                });

                DataFetcher petFriendsDF = (env -> {
                    Pet pet = env.getSource();
                    return env.getDataLoader(petDLName).loadMany((List) pet.friendsIds);
                });

                DataFetcher petOwnerDF = (env -> {
                    Pet pet = env.getSource();
                    return env.getDataLoader(ownerDLName).load(pet.ownerId);
                });


                TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(sdl);
                RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                        .type("Query", builder -> builder
                                .dataFetcher("owners", ownersDF))
                        .type("Owner", builder -> builder
                                .dataFetcher("pets", petsDf))
                        .type("Pet", builder -> builder
                                .dataFetcher("friends", petFriendsDF)
                                .dataFetcher("owner", petOwnerDF))
                        .build();

                query = "{owners{name pets { name friends{name owner {name }}}}}";

                schema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

                graphQL = GraphQL.newGraphQL(schema).build();

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void executeRequestWithDataLoaders(MyState myState, Blackhole blackhole) {
        DataLoader ownerDL = DataLoaderFactory.newDataLoader(ownerBatchLoader);
        DataLoader petDL = DataLoaderFactory.newDataLoader(petBatchLoader);

        DataLoaderRegistry registry = DataLoaderRegistry.newRegistry().register(ownerDLName, ownerDL).register(petDLName, petDL).build();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(myState.query).dataLoaderRegistry(registry).build();
        executionInput.getGraphQLContext().put(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING, true);
        ExecutionResult execute = myState.graphQL.execute(executionInput);
        Assert.assertTrue(execute.isDataPresent());
        Assert.assertTrue(execute.getErrors().isEmpty());
        blackhole.consume(execute);
    }


}
