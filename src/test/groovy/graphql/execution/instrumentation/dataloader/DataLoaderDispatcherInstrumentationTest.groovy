package graphql.execution.instrumentation.dataloader

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

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
}
