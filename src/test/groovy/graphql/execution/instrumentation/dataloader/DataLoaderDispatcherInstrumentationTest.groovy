package graphql.execution.instrumentation.dataloader

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.ExecutorServiceExecutionStrategy
import graphql.execution.batched.BatchedExecutionStrategy
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ForkJoinPool

import static graphql.ExecutionInput.newExecutionInput
import static graphql.StarWarsSchema.starWarsSchema

class DataLoaderDispatcherInstrumentationTest extends Specification {

    class CaptureStrategy extends AsyncExecutionStrategy {
        Instrumentation instrumentation = null

        @Override
        CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
            instrumentation = executionContext.instrumentation
            return super.execute(executionContext, parameters)
        }
    }


    def "dataloader instrumentation is always added and an empty data loader registry is in place"() {

        def captureStrategy = new CaptureStrategy()
        def graphQL = GraphQL.newGraphQL(starWarsSchema).queryExecutionStrategy(captureStrategy).build()
        def executionInput = newExecutionInput().query('{ hero { name } }').build()
        when:
        graphQL.execute(executionInput)
        then:
        executionInput.getDataLoaderRegistry() != null
        def chainedInstrumentation = captureStrategy.instrumentation as ChainedInstrumentation
        chainedInstrumentation.instrumentations.any { instr -> instr instanceof DataLoaderDispatcherInstrumentation }
    }

    def "dispatch is never called if there are no data loaders"() {
        def dataLoaderRegistry = new DataLoaderRegistry() {
            @Override
            void dispatchAll() {
                assert false, "This should not be called when there are no data loaders"
            }
        }
        def graphQL = GraphQL.newGraphQL(starWarsSchema).build()
        def executionInput = newExecutionInput().dataLoaderRegistry(dataLoaderRegistry).query('{ hero { name } }').build()

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
        er.data == [hero: [name: 'R2-D2', friends: [
                [name: 'Luke Skywalker', friends: [
                        [name: 'Han Solo'], [name: 'Leia Organa'], [name: 'C-3PO'], [name: 'R2-D2']]],
                [name: 'Han Solo', friends: [
                        [name: 'Luke Skywalker'], [name: 'Leia Organa'], [name: 'R2-D2']]],
                [name: 'Leia Organa', friends: [
                        [name: 'Luke Skywalker'], [name: 'Han Solo'], [name: 'C-3PO'], [name: 'R2-D2']]]]]
        ]

        where:
        executionStrategyName              | executionStrategy                                               || _
        "AsyncExecutionStrategy"           | new AsyncSerialExecutionStrategy()                              || _
        "AsyncSerialExecutionStrategy"     | new AsyncSerialExecutionStrategy()                              || _
        "BatchedExecutionStrategy"         | new BatchedExecutionStrategy()                                  || _
        "ExecutorServiceExecutionStrategy" | new ExecutorServiceExecutionStrategy(ForkJoinPool.commonPool()) || _
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
}
