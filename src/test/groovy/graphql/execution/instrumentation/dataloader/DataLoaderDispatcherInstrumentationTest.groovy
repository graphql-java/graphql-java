package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class DataLoaderDispatcherInstrumentationTest extends Specification {

    class CountingLoader implements BatchLoader<Object, Object> {
        int invocationCount = 0
        List<Object> loadedKeys = new ArrayList<>()

        @Override
        CompletionStage<List<Object>> load(List<Object> keys) {
            invocationCount++
            loadedKeys.add(keys)
            return CompletableFuture.completedFuture(keys)
        }
    }

    def basic_invocation() {
        given:

        final CountingLoader batchLoader = new CountingLoader()

        DataLoader<Object, Object> dlA = new DataLoader<>(batchLoader)
        DataLoader<Object, Object> dlB = new DataLoader<>(batchLoader)
        DataLoader<Object, Object> dlC = new DataLoader<>(batchLoader)
        DataLoaderRegistry registry = new DataLoaderRegistry()
                .register("a", dlA)
                .register("b", dlB)
                .register("c", dlC)

        DataLoaderDispatcherInstrumentation dispatcher = new DataLoaderDispatcherInstrumentation(registry)
        def instrumentationState = dispatcher.createState()

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("{x}").build()
        InstrumentationExecutionParameters parameters = new InstrumentationExecutionParameters(executionInput, null, instrumentationState)
        InstrumentationContext<CompletableFuture<ExecutionResult>> context = dispatcher.beginExecutionDispatch(parameters)

        // cause some activity
        dlA.load("A")
        dlB.load("B")
        dlC.load("C")

        context.onEnd(null, null)



        expect:
        assert batchLoader.invocationCount == 3

        // will be [[A],[B],[C]]
        assert batchLoader.loadedKeys == [["A"], ["B"], ["C"]]
    }
}
