package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.EngineRunningState
import graphql.GraphQLContext
import graphql.Profiler
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.CoercedVariables
import graphql.execution.ExecutionContext
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.NonNullableFieldValidator
import graphql.execution.ResultPath
import graphql.execution.incremental.AlternativeCallContext
import graphql.schema.GraphQLSchema
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static graphql.Scalars.GraphQLString
import static graphql.execution.ExecutionStepInfo.newExecutionStepInfo
import static graphql.execution.ExecutionStrategyParameters.newParameters

class ExhaustedDataLoaderDispatchStrategyTest extends Specification {

    AtomicInteger batchLoaderInvocations
    DataLoaderRegistry dataLoaderRegistry
    ExecutionContext executionContext
    ExhaustedDataLoaderDispatchStrategy strategy

    ExecutionStrategyParameters rootParams

    def setup() {
        batchLoaderInvocations = new AtomicInteger()
    }

    private void setupStrategy(BatchLoader<String, String> batchLoader) {
        dataLoaderRegistry = new DataLoaderRegistry()
        def dataLoader = DataLoaderFactory.newDataLoader(batchLoader)
        dataLoaderRegistry.register("testLoader", dataLoader)

        def executionInput = ExecutionInput.newExecutionInput()
                .query("{ dummy }")
                .build()
        def engineRunningState = new EngineRunningState(executionInput, Profiler.NO_OP)

        def executionStrategy = new AsyncExecutionStrategy()
        executionContext = new ExecutionContextBuilder()
                .executionId(ExecutionId.generate())
                .graphQLSchema(GraphQLSchema.newSchema().query(
                        graphql.schema.GraphQLObjectType.newObject()
                                .name("Query")
                                .field({ f -> f.name("dummy").type(GraphQLString) })
                                .build()
                ).build())
                .queryStrategy(executionStrategy)
                .mutationStrategy(executionStrategy)
                .subscriptionStrategy(executionStrategy)
                .graphQLContext(GraphQLContext.newContext().build())
                .coercedVariables(CoercedVariables.emptyVariables())
                .dataLoaderRegistry(dataLoaderRegistry)
                .executionInput(executionInput)
                .profiler(Profiler.NO_OP)
                .engineRunningState(engineRunningState)
                .build()

        strategy = new ExhaustedDataLoaderDispatchStrategy(executionContext)

        rootParams = newParameters()
                .executionStepInfo(newExecutionStepInfo()
                        .type(GraphQLString)
                        .path(ResultPath.rootPath())
                        .build())
                .source(new Object())
                .fields(graphql.execution.MergedSelectionSet.newMergedSelectionSet().build())
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .build()
    }

    private BatchLoader<String, String> simpleBatchLoader() {
        return new BatchLoader<String, String>() {
            @Override
            CompletionStage<List<String>> load(List<String> keys) {
                batchLoaderInvocations.incrementAndGet()
                return CompletableFuture.completedFuture(keys)
            }
        }
    }

    def "basic dispatch cycle - finishedFetching triggers dispatch when objectRunning reaches 0"() {
        given:
        setupStrategy(simpleBatchLoader())
        // Load a key so the data loader has pending work
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        when:
        // executionStrategy: increments running count to 1
        strategy.executionStrategy(executionContext, rootParams, 1)
        // newDataLoaderInvocation: sets dataLoaderToDispatch = true; running > 0 so no dispatch yet
        strategy.newDataLoaderInvocation(null)
        // finishedFetching: decrements running to 0; all conditions met -> dispatch fires
        strategy.finishedFetching(executionContext, rootParams)

        // Give async dispatch a moment to complete
        Thread.sleep(100)

        then:
        batchLoaderInvocations.get() == 1
    }

    def "early return in newDataLoaderInvocation when dataLoaderToDispatch already set"() {
        given:
        setupStrategy(simpleBatchLoader())
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        when:
        strategy.executionStrategy(executionContext, rootParams, 1)
        // First call sets dataLoaderToDispatch = true
        strategy.newDataLoaderInvocation(null)
        // Second call: flag already true -> early return at line 223
        strategy.newDataLoaderInvocation(null)
        // Dispatch via finishedFetching
        strategy.finishedFetching(executionContext, rootParams)

        Thread.sleep(100)

        then:
        // Batch loader should be called exactly once despite two newDataLoaderInvocation calls
        batchLoaderInvocations.get() == 1
    }

    def "dispatch triggered from newDataLoaderInvocation when objectRunningCount is already 0"() {
        given:
        setupStrategy(simpleBatchLoader())

        when:
        // executionStrategy: running count = 1
        strategy.executionStrategy(executionContext, rootParams, 1)
        // finishedFetching: running count = 0, but dataLoaderToDispatch is false -> no dispatch
        strategy.finishedFetching(executionContext, rootParams)

        // Now load a key so there's pending work
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        // newDataLoaderInvocation: sets dataLoaderToDispatch = true; running == 0 -> dispatches from line 233
        strategy.newDataLoaderInvocation(null)

        Thread.sleep(100)

        then:
        batchLoaderInvocations.get() == 1
    }

    def "multiple dispatch rounds when data loader invocation happens during dispatch"() {
        given:
        def secondRoundLatch = new CountDownLatch(1)
        AtomicInteger roundCount = new AtomicInteger()

        // A batch loader that on the first call, loads another key (triggering a second dispatch round)
        def chainedBatchLoader = new BatchLoader<String, String>() {
            @Override
            CompletionStage<List<String>> load(List<String> keys) {
                int round = roundCount.incrementAndGet()
                if (round == 1) {
                    // During first batch, load another key to trigger second dispatch
                    dataLoaderRegistry.getDataLoader("testLoader").load("key2")
                    strategy.newDataLoaderInvocation(null)
                }
                if (round == 2) {
                    secondRoundLatch.countDown()
                }
                return CompletableFuture.completedFuture(keys)
            }
        }
        setupStrategy(chainedBatchLoader)

        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        when:
        strategy.executionStrategy(executionContext, rootParams, 1)
        strategy.newDataLoaderInvocation(null)
        strategy.finishedFetching(executionContext, rootParams)

        // Wait for second dispatch round
        def completed = secondRoundLatch.await(2, TimeUnit.SECONDS)

        then:
        completed
        roundCount.get() == 2
    }

    def "executionSerialStrategy clears and re-initializes state"() {
        given:
        setupStrategy(simpleBatchLoader())
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        when:
        // Start with a root execution
        strategy.executionStrategy(executionContext, rootParams, 1)
        // executionSerialStrategy clears state and re-inits running count
        strategy.executionSerialStrategy(executionContext, rootParams)
        // Set dataLoaderToDispatch
        strategy.newDataLoaderInvocation(null)
        // Finish fetching -> should dispatch
        strategy.finishedFetching(executionContext, rootParams)

        Thread.sleep(100)

        then:
        batchLoaderInvocations.get() == 1
    }

    def "alternative call context - subscription creates separate call stack"() {
        given:
        setupStrategy(simpleBatchLoader())
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")
        def altCtx = new AlternativeCallContext()

        when:
        // Also start the initial call stack so it doesn't interfere
        strategy.executionStrategy(executionContext, rootParams, 1)

        // Create subscription call stack
        strategy.newSubscriptionExecution(altCtx)
        // Signal data loader invocation on subscription context
        strategy.newDataLoaderInvocation(altCtx)
        // Complete subscription event -> triggers dispatch on subscription call stack
        strategy.subscriptionEventCompletionDone(altCtx)

        Thread.sleep(100)

        then:
        batchLoaderInvocations.get() == 1
    }

    def "startComplete and stopComplete affect dispatch"() {
        given:
        setupStrategy(simpleBatchLoader())
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        when:
        strategy.executionStrategy(executionContext, rootParams, 1)
        // startComplete increments running count
        strategy.startComplete(rootParams)
        // finishedFetching decrements, but running count is still > 0 due to startComplete
        strategy.finishedFetching(executionContext, rootParams)
        // Set dataLoaderToDispatch
        strategy.newDataLoaderInvocation(null)
        // stopComplete decrements to 0 -> triggers dispatch
        strategy.stopComplete(rootParams)

        Thread.sleep(100)

        then:
        batchLoaderInvocations.get() == 1
    }

    def "deferred call context creates lazy call stack via computeIfAbsent"() {
        given:
        setupStrategy(simpleBatchLoader())
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        // Create params with a deferred call context
        def deferCtx = new AlternativeCallContext(1, 1)
        def deferredParams = newParameters()
                .executionStepInfo(newExecutionStepInfo()
                        .type(GraphQLString)
                        .path(ResultPath.rootPath())
                        .build())
                .source(new Object())
                .fields(graphql.execution.MergedSelectionSet.newMergedSelectionSet().build())
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .deferredCallContext(deferCtx)
                .build()

        when:
        // Start initial execution
        strategy.executionStrategy(executionContext, rootParams, 1)

        // finishedFetching with deferred params triggers lazy call stack creation
        // The computeIfAbsent in getCallStack creates a new CallStack and increments its running count
        // Then finishedFetching decrements it -> running count = 0
        strategy.newDataLoaderInvocation(deferCtx)
        strategy.finishedFetching(executionContext, deferredParams)

        Thread.sleep(100)

        then:
        // The deferred call stack dispatches independently
        batchLoaderInvocations.get() == 1
    }

    /**
     * A CallStack subclass that forces CAS failures to deterministically exercise
     * the retry paths in dispatchImpl's CAS loop. Without this, CAS retries only
     * happen under real thread contention, making coverage non-deterministic.
     *
     * Failures are targeted: only CAS attempts matching a specific state transition
     * (identified by the newState pattern) are failed, so other CAS users like
     * incrementObjectRunningCount/decrementObjectRunningCount are not affected.
     */
    static class ContendedCallStack extends ExhaustedDataLoaderDispatchStrategy.CallStack {
        // The newState value that should trigger a simulated CAS failure
        volatile int failOnNewState = -1
        final AtomicInteger casFailuresRemaining = new AtomicInteger(0)

        @Override
        boolean tryUpdateState(int oldState, int newState) {
            if (newState == failOnNewState && casFailuresRemaining.getAndDecrement() > 0) {
                return false
            }
            return super.tryUpdateState(oldState, newState)
        }
    }

    private void setupStrategyWithCallStack(BatchLoader<String, String> batchLoader, ExhaustedDataLoaderDispatchStrategy.CallStack callStack) {
        dataLoaderRegistry = new DataLoaderRegistry()
        def dataLoader = DataLoaderFactory.newDataLoader(batchLoader)
        dataLoaderRegistry.register("testLoader", dataLoader)

        def executionInput = ExecutionInput.newExecutionInput()
                .query("{ dummy }")
                .build()
        def engineRunningState = new EngineRunningState(executionInput, Profiler.NO_OP)

        def executionStrategy = new AsyncExecutionStrategy()
        executionContext = new ExecutionContextBuilder()
                .executionId(ExecutionId.generate())
                .graphQLSchema(GraphQLSchema.newSchema().query(
                        graphql.schema.GraphQLObjectType.newObject()
                                .name("Query")
                                .field({ f -> f.name("dummy").type(GraphQLString) })
                                .build()
                ).build())
                .queryStrategy(executionStrategy)
                .mutationStrategy(executionStrategy)
                .subscriptionStrategy(executionStrategy)
                .graphQLContext(GraphQLContext.newContext().build())
                .coercedVariables(CoercedVariables.emptyVariables())
                .dataLoaderRegistry(dataLoaderRegistry)
                .executionInput(executionInput)
                .profiler(Profiler.NO_OP)
                .engineRunningState(engineRunningState)
                .build()

        strategy = new ExhaustedDataLoaderDispatchStrategy(executionContext, callStack)

        rootParams = newParameters()
                .executionStepInfo(newExecutionStepInfo()
                        .type(GraphQLString)
                        .path(ResultPath.rootPath())
                        .build())
                .source(new Object())
                .fields(graphql.execution.MergedSelectionSet.newMergedSelectionSet().build())
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .build()
    }

    def "CAS retry in dispatchImpl dispatch path is exercised under contention"() {
        given:
        def contendedCallStack = new ContendedCallStack()
        setupStrategyWithCallStack(simpleBatchLoader(), contendedCallStack)
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        when:
        strategy.executionStrategy(executionContext, rootParams, 1)
        strategy.newDataLoaderInvocation(null)
        // The dispatch-setup CAS in dispatchImpl sets currentlyDispatching=true and
        // dataLoaderToDispatch=false. With objectRunningCount=0, the target newState is:
        // currentlyDispatching(bit0)=1, dataLoaderToDispatch(bit1)=0, objectRunningCount(bits2+)=0
        // = 0b01 = 1
        contendedCallStack.failOnNewState = ExhaustedDataLoaderDispatchStrategy.CallStack.setCurrentlyDispatching(
                ExhaustedDataLoaderDispatchStrategy.CallStack.setDataLoaderToDispatch(0, false), true)
        contendedCallStack.casFailuresRemaining.set(1)
        strategy.finishedFetching(executionContext, rootParams)

        Thread.sleep(200)

        then:
        batchLoaderInvocations.get() == 1
    }

    def "CAS retry in dispatchImpl early-exit path is exercised under contention"() {
        given:
        def doneLatch = new CountDownLatch(1)
        AtomicInteger roundCount = new AtomicInteger()
        def contendedCallStack = new ContendedCallStack()

        def chainedBatchLoader = new BatchLoader<String, String>() {
            @Override
            CompletionStage<List<String>> load(List<String> keys) {
                int round = roundCount.incrementAndGet()
                if (round == 1) {
                    // During first batch, load another key to trigger second dispatch round
                    dataLoaderRegistry.getDataLoader("testLoader").load("key2")
                    strategy.newDataLoaderInvocation(null)
                }
                if (round == 2) {
                    // Inject a CAS failure targeting the early-exit path. After round 2
                    // completes, the recursive dispatchImpl sees dataLoaderToDispatch=false
                    // and tries to set currentlyDispatching=false. The target newState is 0
                    // (all bits cleared: no dispatching, no dataLoader pending, objectRunning=0).
                    contendedCallStack.failOnNewState = ExhaustedDataLoaderDispatchStrategy.CallStack.setCurrentlyDispatching(0, false)
                    contendedCallStack.casFailuresRemaining.set(1)
                    doneLatch.countDown()
                }
                return CompletableFuture.completedFuture(keys)
            }
        }
        setupStrategyWithCallStack(chainedBatchLoader, contendedCallStack)
        dataLoaderRegistry.getDataLoader("testLoader").load("key1")

        when:
        strategy.executionStrategy(executionContext, rootParams, 1)
        strategy.newDataLoaderInvocation(null)
        strategy.finishedFetching(executionContext, rootParams)

        def completed = doneLatch.await(2, TimeUnit.SECONDS)

        then:
        completed
        roundCount.get() == 2
    }
}
