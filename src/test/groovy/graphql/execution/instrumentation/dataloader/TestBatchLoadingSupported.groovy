package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsData
import graphql.TypeResolutionEnvironment
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.ExecutorServiceExecutionStrategy
import graphql.execution.batched.BatchedExecutionStrategy
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import graphql.schema.idl.MapEnumValuesProvider
import graphql.schema.idl.RuntimeWiring
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ForkJoinPool
import java.util.stream.Collectors

import static graphql.TestUtil.schemaFile
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class TestBatchLoadingSupported extends Specification {

    int rawCharacterLoadCount = 0
    int batchFunctionLoadCount = 0
    int naiveLoadCount = 0

    void setup() {
        rawCharacterLoadCount = 0
        batchFunctionLoadCount = 0
        naiveLoadCount = 0
    }

    Object getCharacterData(String id) {
        rawCharacterLoadCount++
        if (StarWarsData.humanData[id] != null) return StarWarsData.humanData[id]
        if (StarWarsData.droidData[id] != null) return StarWarsData.droidData[id]
        return null
    }

    List<Object> getCharacterDataViaBatchHTTPApi(List<String> keys) {
        keys.stream().map({ id -> getCharacterData(id) }).collect(Collectors.toList())
    }

    // a batch loader function that will be called with N or more keys for batch loading
    BatchLoader<String, Object> characterBatchLoader = new BatchLoader<String, Object>() {
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
    def characterDataLoader = new DataLoader<String, Object>(characterBatchLoader)

    // we define the normal StarWars data fetchers so we can point them at our data loader
    DataFetcher humanDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            String id = environment.arguments.id
            naiveLoadCount += 1
            characterDataLoader.load(id)
        }
    }


    DataFetcher droidDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            String id = environment.arguments.id
            naiveLoadCount += 1
            characterDataLoader.load(id)
        }
    }

    DataFetcher heroDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            naiveLoadCount += 1
            characterDataLoader.load('2001') // R2D2
        }
    }

    DataFetcher friendsDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            List<String> friendIds = environment.source.friends
            naiveLoadCount += friendIds.size()
            return characterDataLoader.loadMany(friendIds)
        }
    }

    TypeResolver characterTypeResolver = new TypeResolver() {
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

    def "basic batch loading is possible via instrumentation interception of Execution Strategies"() {

        given:
        def dlRegistry = new DataLoaderRegistry().register("characters", characterDataLoader)
        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation(dlRegistry)

        def graphql = GraphQL.newGraphQL(schema).instrumentation(batchingInstrumentation).build()

        when:

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

    @Unroll
    def "ensure  DataLoaderDispatcherInstrumentation works for  #name"() {

        given:
        def dlRegistry = new DataLoaderRegistry().register("characters", characterDataLoader)

        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation(dlRegistry)

        def graphql = GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(executionStrategy)
                .instrumentation(batchingInstrumentation).build()

        when:

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

        where:
        name                               | executionStrategy                                               || _
        "AsyncExecutionStrategy"           | new AsyncSerialExecutionStrategy()                              || _
        "AsyncSerialExecutionStrategy"     | new AsyncSerialExecutionStrategy()                              || _
        "BatchedExecutionStrategy"         | new BatchedExecutionStrategy()                                  || _
        "ExecutorServiceExecutionStrategy" | new ExecutorServiceExecutionStrategy(ForkJoinPool.commonPool()) || _

    }


    def "non list queries work as expected"() {

        given:
        def dlRegistry = new DataLoaderRegistry().register("characters", characterDataLoader)
        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation(dlRegistry)

        def graphql = GraphQL.newGraphQL(schema).instrumentation(batchingInstrumentation).build()

        when:
        def query = """
        query {
            arToo : hero {
                name 
                friends {
                    name
                }
            }

            tinBox : hero {
                name 
                friends {
                    name
                }
            }
        }
        """

        def asyncResult = graphql.executeAsync(ExecutionInput.newExecutionInput().query(query))

        def er = asyncResult.join()

        then:
        er.data == [arToo : [name: "R2-D2", friends: [[name: "Luke Skywalker"], [name: "Han Solo"], [name: "Leia Organa"]]],
                    tinBox: [name: "R2-D2", friends: [[name: "Luke Skywalker"], [name: "Han Solo"], [name: "Leia Organa"]]]
        ]

        rawCharacterLoadCount == 4
        batchFunctionLoadCount == 2
        naiveLoadCount == 8
    }


}
