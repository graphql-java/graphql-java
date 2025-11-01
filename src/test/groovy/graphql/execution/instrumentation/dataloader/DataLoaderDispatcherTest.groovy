package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.pubsub.CapturingSubscriber
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.awaitility.Awaitility
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static graphql.ExecutionInput.newExecutionInput
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DataLoaderDispatcherTest extends Specification {


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

    def expectedQueryData = [hero: [name: 'R2-D2', friends: [
            [name: 'Luke Skywalker', friends: [
                    [name: 'Han Solo'], [name: 'Leia Organa'], [name: 'C-3PO'], [name: 'R2-D2']]],
            [name: 'Han Solo', friends: [
                    [name: 'Luke Skywalker'], [name: 'Leia Organa'], [name: 'R2-D2']]],
            [name: 'Leia Organa', friends: [
                    [name: 'Luke Skywalker'], [name: 'Han Solo'], [name: 'C-3PO'], [name: 'R2-D2']]]]]
    ]


    @Unroll
    def "basic dataloader dispatch test"() {
        def dispatchedCalled = false
        def dataLoaderRegistry = new DataLoaderRegistry()
        def dataLoader = DataLoaderFactory.newDataLoader(new BatchLoader() {
            @Override
            CompletionStage<List> load(List keys) {
                return CompletableFuture.completedFuture(keys)
            }
        })
        dataLoaderRegistry.register("someDataLoader", dataLoader)

        def graphQL = GraphQL.newGraphQL(starWarsSchema).build()
        def executionInput = newExecutionInput().dataLoaderRegistry(dataLoaderRegistry).query('{ hero { name } }').build()
        executionInput.getGraphQLContext().putAll(contextKey == null ? Collections.emptyMap() : [(contextKey): true])

        when:
        def er = graphQL.executeAsync(executionInput)
        Awaitility.await().until { er.isDone() }
        then:
        er.get().data == [hero: [name: 'R2-D2']]

        where:
        contextKey << [ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, null]

    }

    def "enhanced execution input is respected"() {

        def starWarsWiring = new StarWarsDataLoaderWiring()


        DataLoaderRegistry startingDataLoaderRegistry = new DataLoaderRegistry()
        def enhancedDataLoaderRegistry = starWarsWiring.newDataLoaderRegistry()

        def enhancingInstrumentation = new SimplePerformantInstrumentation() {

            @Override
            ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters, InstrumentationState state) {
                assert executionInput.getDataLoaderRegistry() == startingDataLoaderRegistry
                return executionInput.transform({ builder -> builder.dataLoaderRegistry(enhancedDataLoaderRegistry) })
            }
        }

        def chainedInstrumentation = new ChainedInstrumentation([enhancingInstrumentation])

        def graphql = GraphQL.newGraphQL(starWarsWiring.schema)
                .instrumentation(chainedInstrumentation).build()

        def executionInput = newExecutionInput()
                .dataLoaderRegistry(startingDataLoaderRegistry)
                .query(query).build()

        when:
        def er = graphql.executeAsync(executionInput).join()
        then:
        er.data == expectedQueryData
    }


    @Unroll
    def "ensure DataLoaderDispatcher works for async serial execution strategy"() {

        given:
        def executionStrategy = new AsyncSerialExecutionStrategy()
        def starWarsWiring = new StarWarsDataLoaderWiring()
        def dlRegistry = starWarsWiring.newDataLoaderRegistry()


        def graphql = GraphQL.newGraphQL(starWarsWiring.schema)
                .queryExecutionStrategy(executionStrategy)
                .build()

        when:

        def asyncResult = graphql.executeAsync(newExecutionInput().query(query)
                .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                .dataLoaderRegistry(dlRegistry))


        Awaitility.await().atMost(Duration.ofMillis(200)).until { -> asyncResult.isDone() }
        def er = asyncResult.join()

        then:
        er.data == expectedQueryData

        where:
        contextKey << [ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, null]

    }

    @Unroll
    def "basic batch loading is possible"() {

        given:
        def starWarsWiring = new StarWarsDataLoaderWiring()
        def dlRegistry = starWarsWiring.newDataLoaderRegistry()

        def graphql = GraphQL.newGraphQL(starWarsWiring.schema).build()

        when:

        def asyncResult = graphql.executeAsync(newExecutionInput().query(query)
                .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                .dataLoaderRegistry(dlRegistry))

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
        starWarsWiring.rawCharacterLoadCount == 5
        //
        // our batch load API only gets called thrice, for R2D2, for his friends and for their friends
        starWarsWiring.batchFunctionLoadCount == 3
        //
        // if we didn't have batch loading it would have these many character load calls
        starWarsWiring.naiveLoadCount == 15

        where:
        contextKey << [ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, null]

    }


    @Unroll
    def "non list queries work as expected"() {

        given:
        def starWarsWiring = new StarWarsDataLoaderWiring()
        def dlRegistry = starWarsWiring.newDataLoaderRegistry()

        def graphql = GraphQL.newGraphQL(starWarsWiring.schema)
                .build()

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

        def asyncResult = graphql.executeAsync(newExecutionInput().query(query)
                .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                .dataLoaderRegistry(dlRegistry))

        def er = asyncResult.join()

        then:
        er.data == [arToo : [name: "R2-D2", friends: [[name: "Luke Skywalker"], [name: "Han Solo"], [name: "Leia Organa"]]],
                    tinBox: [name: "R2-D2", friends: [[name: "Luke Skywalker"], [name: "Han Solo"], [name: "Leia Organa"]]]
        ]

        starWarsWiring.rawCharacterLoadCount == 4
        starWarsWiring.batchFunctionLoadCount == 2
        starWarsWiring.naiveLoadCount == 8

        where:
        contextKey << [ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, null]

    }

    @Unroll
    def "can be efficient with lazily computed data loaders"() {

        def sdl = '''
            type Query {
                field : String
            }
        '''

        BatchLoader batchLoader = { keys -> CompletableFuture.completedFuture(keys) }

        def df = { env ->
            def dataLoader = env.getDataLoader("key")
            return dataLoader.load("working as expected")
        } as DataFetcher
        def runtimeWiring = newRuntimeWiring().type(
                newTypeWiring("Query").dataFetcher("field", df).build()
        ).build()

        def graphql = TestUtil.graphQL(sdl, runtimeWiring).build()

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        def loader = DataLoaderFactory.newDataLoader("key", batchLoader)
        dataLoaderRegistry.register("key", loader)

        when:
        def executionInput = newExecutionInput().dataLoaderRegistry(dataLoaderRegistry).query('{ field }').build()
        executionInput.getGraphQLContext().putAll(contextKey == null ? Collections.emptyMap() : [(contextKey): true]);
        def erCF = graphql.executeAsync(executionInput)

        then:
        Awaitility.await().until { erCF.isDone() }
        erCF.get().errors.isEmpty()
        erCF.get().data["field"] == "working as expected"

        where:
        contextKey << [ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, null]
    }

    @Unroll
    def "handles deep async queries when a data loader registry is present"() {
        given:
        def support = new DeepDataFetchers()
        def dummyDataloaderRegistry = new DataLoaderRegistry()
        def graphql = GraphQL.newGraphQL(support.schema())
                .build()
        def depth = 50

        when:
        def asyncResult = graphql.executeAsync(
                newExecutionInput()
                        .query(support.buildQuery(depth))
                        .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                        .dataLoaderRegistry(dummyDataloaderRegistry)
        )
        def er = asyncResult.join()

        then:
        er.errors.isEmpty()
        er.data == support.buildResponse(depth)

        where:
        contextKey << [ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, null]

    }

    @Unroll
    def "issue 3662 - dataloader dispatching can work with subscriptions"() {

        def sdl = '''
            type Query {
                field : String
            }
            
            type Subscription {
                onSub : OnSub
            }
            
            type OnSub {
                x : String
                y : String
            }
        '''

        // the dispatching is ALWAYS so not really batching but it completes
        BatchLoader batchLoader = { keys ->
            CompletableFuture.supplyAsync {
                Thread.sleep(50) // some delay
                keys
            }
        }

        DataFetcher dlDF = { DataFetchingEnvironment env ->
            def dataLoader = env.getDataLoader("dl")
            return dataLoader.load("working as expected")
        }
        DataFetcher dlSub = { DataFetchingEnvironment env ->
            return Mono.just([x: "X", y: "Y"])
        }
        def runtimeWiring = newRuntimeWiring()
                .type(newTypeWiring("OnSub")
                        .dataFetcher("x", dlDF)
                        .dataFetcher("y", dlDF)
                        .build()
                )
                .type(newTypeWiring("Subscription")
                        .dataFetcher("onSub", dlSub)
                        .build()
                )
                .build()

        def graphql = TestUtil.graphQL(sdl, runtimeWiring).build()

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("dl", DataLoaderFactory.newDataLoader(batchLoader))

        when:
        def query = """
        subscription s {
            onSub {
                x, y
            }
        }
        """
        def executionInput = newExecutionInput()
                .dataLoaderRegistry(dataLoaderRegistry)
                .query(query)
                .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                .build()
        def er = graphql.execute(executionInput)

        then:
        er.errors.isEmpty()
        def subscriber = new CapturingSubscriber()
        Publisher pub = er.data
        pub.subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.isDone())

        subscriber.getEvents().size() == 1

        def msgER = subscriber.getEvents()[0] as ExecutionResult
        msgER.data == [onSub: [x: "working as expected", y: "working as expected"]]

        where:
        contextKey << [ENABLE_DATA_LOADER_CHAINING, ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, null]
    }
}
