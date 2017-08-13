package graphql.execution.instrumentation

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsData
import graphql.TypeResolutionEnvironment
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import graphql.schema.idl.MapEnumValuesProvider
import graphql.schema.idl.RuntimeWiring
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.stream.Collectors

import static graphql.TestUtil.schemaFile
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class TestBatchLoadingSupported extends Specification {

    static int rawCharacterLoadCount = 0
    static int batchFunctionLoadCount = 0
    static int naiveLoadCount = 0

    static Object getCharacterData(String id) {
        rawCharacterLoadCount++
        if (StarWarsData.humanData[id] != null) return StarWarsData.humanData[id]
        if (StarWarsData.droidData[id] != null) return StarWarsData.droidData[id]
        return null
    }

    static List<Object> getCharacterDataViaBatchHTTPApi(List<String> keys) {
        keys.stream().map({ id -> getCharacterData(id) }).collect(Collectors.toList())
    }

    // a batch loader function that will be called with N or more keys for batch loading
    static BatchLoader<String, Object> characterBatchLoader = new BatchLoader<String, Object>() {
        @Override
        CompletionStage<List<Object>> load(List<String> keys) {
            batchFunctionLoadCount++

            //
            // direct return of values
            //CompletableFuture.completedFuture(getCharacterDataViaBatchHTTPApi(keys))
            //
            // or
            //
            // async supply of values
            CompletableFuture.supplyAsync({
                return getCharacterDataViaBatchHTTPApi(keys)
            })
        }

    }

    // a data loader for characters that points to the character batch loader
    static def characterDataLoader = new DataLoader<String, Object>(characterBatchLoader)

    // we define the normal StarWars data fetchers so we can point them at our data loader
    static DataFetcher humanDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            String id = environment.arguments.id
            naiveLoadCount += 1
            characterDataLoader.load(id)
        }
    }


    static DataFetcher droidDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            String id = environment.arguments.id
            naiveLoadCount += 1
            characterDataLoader.load(id)
        }
    }

    static DataFetcher heroDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            naiveLoadCount += 1
            characterDataLoader.load('2001') // R2D2
        }
    }

    static DataFetcher friendsDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            List<String> friendIds = environment.source.friends
            naiveLoadCount += friendIds.size()
            return characterDataLoader.loadMany(friendIds)
        }
    }

    static TypeResolver characterTypeResolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            String id = env.getObject().id
            if (StarWarsData.humanData[id] != null)
                return env.schema.getType("Human") as GraphQLObjectType
            if (StarWarsData.droidData[id] != null)
                return env.schema.getType("Droid") as GraphQLObjectType
            return null
        }
    }


    def starWarsWiring() {
        def episodeValuesProvider = new MapEnumValuesProvider([NEWHOPE: 4, EMPIRE: 5, JEDI: 6])
        def episodeWiring = newTypeWiring("Episode").enumValues(episodeValuesProvider).build()

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("QueryType")
                .dataFetchers(
                [
                        "hero" : heroDataFetcher,
                        "human": humanDataFetcher,
                        "droid": droidDataFetcher
                ])
        )
                .type(newTypeWiring("Human")
                .dataFetcher("friends", friendsDataFetcher)
        )
                .type(newTypeWiring("Droid")
                .dataFetcher("friends", friendsDataFetcher)
        )

                .type(newTypeWiring("Character")
                .typeResolver(characterTypeResolver)
        )
                .type(episodeWiring)
                .build()
        wiring
    }

    def schema = schemaFile("starWarsSchema.graphqls", starWarsWiring())


    def "basic batch loading is possible via instrumentation interception of Execution Strategies"() {

        given:
        def batchingInstrumentation = new NoOpInstrumentation() {
            @Override
            InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
                return new InstrumentationContext<CompletableFuture<ExecutionResult>>() {
                    @Override
                    void onEnd(CompletableFuture<ExecutionResult> result, Throwable t) {
                        //
                        // this causes "batched" futures to actually be turned into batch loads
                        characterDataLoader.dispatch()
                    }
                }
            }
        }

        def graphql = GraphQL.newGraphQL(schema).instrumentation(batchingInstrumentation).build()

        when:
        def query = """
        query {
            hero {
                name 
                friends {
                    name
                    friends {
                       name
                    } 
                }
            }
        }
        """

        def asyncResult = graphql.executeAsync(ExecutionInput.newExecutionInput().query(query))

        def er = asyncResult.join()

        then:
        er.data == [hero: [name: 'R2-D2', friends: [
                [name: 'Luke Skywalker', friends: [
                        [name: 'Han Solo'], [name: 'Leia Organa'], [name: 'C-3PO'], [name: 'R2-D2']]],
                [name: 'Han Solo', friends: [
                        [name: 'Luke Skywalker'], [name: 'Leia Organa'], [name: 'R2-D2']]],
                [name: 'Leia Organa', friends: [
                        [name: 'Luke Skywalker'], [name: 'Han Solo'], [name: 'C-3PO'], [name: 'R2-D2']]]]]
        ]

        //
        // there are five characters in this query however we have asked for their details over and over
        // and yet we only actually load the objects up five times
        rawCharacterLoadCount == 5
        //
        // our batch load API only gets called thrice, for R2D2, for his friends and for their friends
        batchFunctionLoadCount == 3
        //
        // if we didn't have batch loading it would have these many character load calls
        naiveLoadCount == 15
    }
}
