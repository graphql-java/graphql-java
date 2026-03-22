package graphql.execution.instrumentation.dataloader

import graphql.EngineRunningState
import graphql.ExecutionInput
import graphql.GraphQLContext
import graphql.Profiler
import graphql.Scalars
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.CoercedVariables
import graphql.execution.ExecutionContextBuilder
import graphql.execution.ExecutionId
import graphql.execution.ExecutionStepInfo
import graphql.execution.ExecutionStrategyParameters
import graphql.execution.MergedSelectionSet
import graphql.execution.NonNullableFieldValidator
import graphql.execution.ResultPath
import graphql.execution.ValueUnboxer
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

import static graphql.StarWarsSchema.starWarsSchema

/**
 * Tests for concurrency-dependent code paths in {@link PerLevelDataLoaderDispatchStrategy}
 * that are otherwise non-deterministically covered by integration tests.
 */
class PerLevelDataLoaderDispatchStrategyTest extends Specification {

    def executionContext
    def strategy

    void setup() {
        def dummyStrategy = new AsyncExecutionStrategy()
        def ei = ExecutionInput.newExecutionInput("{ hero { name } }").build()
        def builder = ExecutionContextBuilder.newExecutionContextBuilder()
                .instrumentation(SimplePerformantInstrumentation.INSTANCE)
                .executionId(ExecutionId.from("test"))
                .graphQLSchema(starWarsSchema)
                .queryStrategy(dummyStrategy)
                .mutationStrategy(dummyStrategy)
                .subscriptionStrategy(dummyStrategy)
                .coercedVariables(CoercedVariables.emptyVariables())
                .graphQLContext(GraphQLContext.newContext().build())
                .executionInput(ei)
                .root("root")
                .dataLoaderRegistry(new DataLoaderRegistry())
                .locale(Locale.getDefault())
                .valueUnboxer(ValueUnboxer.DEFAULT)
                .profiler(Profiler.NO_OP)
                .engineRunningState(new EngineRunningState(ei, Profiler.NO_OP))
        executionContext = builder.build()
        strategy = new PerLevelDataLoaderDispatchStrategy(executionContext)
    }

    private ExecutionStrategyParameters paramsAtLevel(int level) {
        def path = ResultPath.rootPath()
        for (int i = 0; i < level; i++) {
            path = path.segment("f" + i)
        }
        return ExecutionStrategyParameters.newParameters()
                .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo()
                        .type(Scalars.GraphQLString)
                        .path(path)
                        .build())
                .fields(MergedSelectionSet.newMergedSelectionSet().build())
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .path(path)
                .build()
    }

    def "markLevelAsDispatchedIfReady returns false when level already dispatched"() {
        given:
        def callStack = strategy.initialCallStack
        def dispatchedLevels = callStack.dispatchedLevels

        and: "set up level 0 via executionStrategy and dispatch level 1 via fieldFetched"
        def rootParams = paramsAtLevel(0)
        strategy.executionStrategy(executionContext, rootParams, 1)
        def level1Params = paramsAtLevel(1)
        strategy.fieldFetched(executionContext, level1Params,
                { env -> null } as DataFetcher,
                "value",
                { -> null } as Supplier<DataFetchingEnvironment>)

        and: "make isLevelReady(2) return true by matching completionFinished to executeObjectCalls at level 0"
        def state0 = callStack.get(0)
        callStack.tryUpdateLevel(0, state0, state0.increaseHappenedCompletionFinishedCount())

        expect:
        dispatchedLevels.contains(1)

        when: "first dispatch of level 2"
        def firstResult = strategy.markLevelAsDispatchedIfReady(2, callStack)

        then:
        firstResult
        dispatchedLevels.contains(2)

        when: "second dispatch of level 2 (simulates another thread arriving late)"
        def secondResult = strategy.markLevelAsDispatchedIfReady(2, callStack)

        then:
        !secondResult
    }

    def "concurrent onCompletionFinished races to dispatch same level"() {
        given:
        def rootParams = paramsAtLevel(0)
        strategy.executionStrategy(executionContext, rootParams, 1)

        and: "increment executeObjectCalls at level 0 from 1 to 2"
        def level0Params = paramsAtLevel(0)
        strategy.executeObject(executionContext, level0Params, 1)

        and: "dispatch level 1 via fieldFetched"
        def level1Params = paramsAtLevel(1)
        strategy.fieldFetched(executionContext, level1Params,
                { env -> null } as DataFetcher,
                "value",
                { -> null } as Supplier<DataFetchingEnvironment>)

        when: "two threads concurrently complete level 0"
        def startLatch = new CountDownLatch(1)
        def executor = Executors.newFixedThreadPool(2)

        def task = {
            startLatch.await()
            strategy.executeObjectOnFieldValuesInfo(Collections.emptyList(), level0Params)
        } as Runnable

        executor.submit(task)
        executor.submit(task)
        startLatch.countDown()
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        then: "level 2 is dispatched exactly once (regardless of which thread won)"
        strategy.initialCallStack.dispatchedLevels.contains(2)
    }

    def "executeObjectOnFieldValuesException calls onCompletionFinished"() {
        given:
        def rootParams = paramsAtLevel(0)
        strategy.executionStrategy(executionContext, rootParams, 1)

        and: "dispatch level 1 via fieldFetched"
        def level1Params = paramsAtLevel(1)
        strategy.fieldFetched(executionContext, level1Params,
                { env -> null } as DataFetcher,
                "value",
                { -> null } as Supplier<DataFetchingEnvironment>)

        when:
        def level2Params = paramsAtLevel(2)
        strategy.executeObjectOnFieldValuesException(
                new RuntimeException("test error"), level2Params)

        then:
        strategy.initialCallStack.get(2).happenedCompletionFinishedCount > 0
    }

    def "executionStrategyOnFieldValuesException calls onCompletionFinished"() {
        given:
        def rootParams = paramsAtLevel(0)
        strategy.executionStrategy(executionContext, rootParams, 1)

        when:
        strategy.executionStrategyOnFieldValuesException(
                new RuntimeException("test error"), rootParams)

        then:
        strategy.initialCallStack.get(0).happenedCompletionFinishedCount > 0
    }
}
