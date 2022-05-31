package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import static graphql.ExecutionInput.newExecutionInput
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DataLoaderDispatcherInstrumentationTest extends Specification {

    class CaptureStrategy extends AsyncExecutionStrategy {
        Instrumentation instrumentation = null

        @Override
        CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
            instrumentation = executionContext.instrumentation
            return super.execute(executionContext, parameters)
        }
    }


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


    def "dataloader instrumentation is always added and an empty data loader registry is in place"() {

        def captureStrategy = new CaptureStrategy()
        def graphQL = GraphQL.newGraphQL(starWarsSchema).queryExecutionStrategy(captureStrategy)
                .instrumentation(new SimpleInstrumentation())
                .build()
        def executionInput = newExecutionInput().query('{ hero { name } }').build()
        when:
        graphQL.execute(executionInput)
        then:
        executionInput.getDataLoaderRegistry() != null
        def chainedInstrumentation = captureStrategy.instrumentation as ChainedInstrumentation
        chainedInstrumentation.instrumentations.any { instr -> instr instanceof DataLoaderDispatcherInstrumentation }
    }

    def "dispatch is never called if data loader registry is not set"() {
        def dataLoaderRegistry = new DataLoaderRegistry() {
            @Override
            void dispatchAll() {
                assert false, "This should not be called when there are no data loaders"
            }
        }
        def graphQL = GraphQL.newGraphQL(starWarsSchema).build()
        def executionInput = newExecutionInput().query('{ hero { name } }').build()

        when:
        def er = graphQL.execute(executionInput)
        then:
        er.errors.isEmpty()
    }

    def "dispatch is called if there are data loaders"() {
        def dispatchedCalled = false
        def dataLoaderRegistry = new DataLoaderRegistry() {
            @Override
            void dispatchAll() {
                dispatchedCalled = true
                super.dispatchAll()
            }
        }
        def dataLoader = DataLoader.newDataLoader(new BatchLoader() {
            @Override
            CompletionStage<List> load(List keys) {
                return CompletableFuture.completedFuture(keys)
            }
        })
        dataLoaderRegistry.register("someDataLoader", dataLoader)

        def graphQL = GraphQL.newGraphQL(starWarsSchema).build()
        def executionInput = newExecutionInput().dataLoaderRegistry(dataLoaderRegistry).query('{ hero { name } }').build()

        when:
        def er = graphQL.execute(executionInput)
        then:
        er.errors.isEmpty()
        dispatchedCalled
    }

    def "enhanced execution input is respected"() {

        def starWarsWiring = new StarWarsDataLoaderWiring()


        DataLoaderRegistry startingDataLoaderRegistry = new DataLoaderRegistry();
        def enhancedDataLoaderRegistry = starWarsWiring.newDataLoaderRegistry()

        def dlInstrumentation = new DataLoaderDispatcherInstrumentation()
        def enhancingInstrumentation = new SimpleInstrumentation() {
            @Override
            ExecutionInput instrumentExecutionInput(ExecutionInput executionInput, InstrumentationExecutionParameters parameters) {
                assert executionInput.getDataLoaderRegistry() == startingDataLoaderRegistry
                return executionInput.transform({ builder -> builder.dataLoaderRegistry(enhancedDataLoaderRegistry) })
            }
        }

        def chainedInstrumentation = new ChainedInstrumentation([dlInstrumentation, enhancingInstrumentation])

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
    def "ensure DataLoaderDispatcherInstrumentation works for #executionStrategyName"() {

        given:
        def starWarsWiring = new StarWarsDataLoaderWiring()
        def dlRegistry = starWarsWiring.newDataLoaderRegistry()

        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation()

        def graphql = GraphQL.newGraphQL(starWarsWiring.schema)
                .queryExecutionStrategy(executionStrategy)
                .instrumentation(batchingInstrumentation).build()

        when:

        def asyncResult = graphql.executeAsync(newExecutionInput().query(query).dataLoaderRegistry(dlRegistry))

        def er = asyncResult.join()

        then:
        er.data == expectedQueryData

        where:
        executionStrategyName          | executionStrategy                  || _
        "AsyncExecutionStrategy"       | new AsyncSerialExecutionStrategy() || _
        "AsyncSerialExecutionStrategy" | new AsyncSerialExecutionStrategy() || _
    }

    def "basic batch loading is possible via instrumentation interception of Execution Strategies"() {

        given:
        def starWarsWiring = new StarWarsDataLoaderWiring()
        def dlRegistry = starWarsWiring.newDataLoaderRegistry()
        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation()

        def graphql = GraphQL.newGraphQL(starWarsWiring.schema).instrumentation(batchingInstrumentation).build()

        when:

        def asyncResult = graphql.executeAsync(newExecutionInput().query(query).dataLoaderRegistry(dlRegistry))

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
    }


    def "non list queries work as expected"() {

        given:
        def starWarsWiring = new StarWarsDataLoaderWiring()
        def dlRegistry = starWarsWiring.newDataLoaderRegistry()
        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation()

        def graphql = GraphQL.newGraphQL(starWarsWiring.schema).instrumentation(batchingInstrumentation).build()

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

        def asyncResult = graphql.executeAsync(newExecutionInput().query(query).dataLoaderRegistry(dlRegistry))

        def er = asyncResult.join()

        then:
        er.data == [arToo : [name: "R2-D2", friends: [[name: "Luke Skywalker"], [name: "Han Solo"], [name: "Leia Organa"]]],
                    tinBox: [name: "R2-D2", friends: [[name: "Luke Skywalker"], [name: "Han Solo"], [name: "Leia Organa"]]]
        ]

        starWarsWiring.rawCharacterLoadCount == 4
        starWarsWiring.batchFunctionLoadCount == 2
        starWarsWiring.naiveLoadCount == 8
    }

    def "can be efficient with lazily computed data loaders"() {

        def sdl = '''
            type Query {
                field : String
            }
        '''

        BatchLoader batchLoader = { keys -> CompletableFuture.completedFuture(keys) }

        DataFetcher df = { env ->
            def dataLoader = env.getDataLoaderRegistry().computeIfAbsent("key", { key -> DataLoader.newDataLoader(batchLoader) })

            return dataLoader.load("working as expected")
        }
        def runtimeWiring = newRuntimeWiring().type(
                newTypeWiring("Query").dataFetcher("field", df).build()
        ).build()

        def graphql = TestUtil.graphQL(sdl, runtimeWiring).build()

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()

        when:
        def executionInput = newExecutionInput().dataLoaderRegistry(dataLoaderRegistry).query('{ field }').build()
        def er = graphql.execute(executionInput)

        then:
        er.errors.isEmpty()
        er.data["field"] == "working as expected"
    }

    def "handles deep async queries when a data loader registry is present"() {
        given:
        def support = new DeepDataFetchers()
        def dummyDataloaderRegistry = new DataLoaderRegistry()
        def batchingInstrumentation = new DataLoaderDispatcherInstrumentation()
        def graphql = GraphQL.newGraphQL(support.schema())
                .instrumentation(batchingInstrumentation)
                .build()
        // FieldLevelTrackingApproach uses LevelMaps with a default size of 16.
        // Use a value greater than 16 to ensure that the underlying LevelMaps are resized
        // as expected
        def depth = 50

        when:
        def asyncResult = graphql.executeAsync(
                newExecutionInput()
                        .query(support.buildQuery(depth))
                        .dataLoaderRegistry(dummyDataloaderRegistry)
        )
        def er = asyncResult.join()

        then:
        er.errors.isEmpty()
        er.data == support.buildResponse(depth)
    }
}
