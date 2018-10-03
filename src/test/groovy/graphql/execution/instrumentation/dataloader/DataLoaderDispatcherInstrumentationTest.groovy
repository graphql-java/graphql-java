package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.*
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
}
