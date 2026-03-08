package graphql.execution.instrumentation.dataloader;

import graphql.ExecutionInput;
import graphql.GraphQLContext;
import graphql.Profiler;
import graphql.execution.CoercedVariables;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionContextBuilder;
import graphql.execution.ExecutionId;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.MergedSelectionSet;
import graphql.execution.NonNullableFieldValidator;
import graphql.execution.ResultPath;
import graphql.execution.ValueUnboxer;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.dataloader.DataLoaderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static graphql.StarWarsSchema.starWarsSchema;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for concurrency-dependent code paths in {@link PerLevelDataLoaderDispatchStrategy}
 * that are otherwise non-deterministically covered by integration tests.
 */
public class PerLevelDataLoaderDispatchStrategyTest {

    private ExecutionContext executionContext;
    private PerLevelDataLoaderDispatchStrategy strategy;

    @BeforeEach
    void setUp() {
        ExecutionStrategy dummyStrategy = new graphql.execution.AsyncExecutionStrategy();
        ExecutionInput ei = ExecutionInput.newExecutionInput("{ hero { name } }").build();
        ExecutionContextBuilder builder = ExecutionContextBuilder.newExecutionContextBuilder()
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
                .engineRunningState(new graphql.EngineRunningState(ei, Profiler.NO_OP));
        executionContext = builder.build();
        strategy = new PerLevelDataLoaderDispatchStrategy(executionContext);
    }

    private ExecutionStrategyParameters paramsAtLevel(int level) {
        ResultPath path = ResultPath.rootPath();
        for (int i = 0; i < level; i++) {
            path = path.segment("f" + i);
        }
        return ExecutionStrategyParameters.newParameters()
                .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo()
                        .type(graphql.Scalars.GraphQLString)
                        .path(path)
                        .build())
                .fields(MergedSelectionSet.newMergedSelectionSet().build())
                .nonNullFieldValidator(new NonNullableFieldValidator(executionContext))
                .path(path)
                .build();
    }

    /**
     * Tests that when two calls try to dispatch the same level,
     * the second call correctly returns false from markLevelAsDispatchedIfReady.
     */
    @Test
    void markLevelAsDispatchedIfReady_returnsFalse_whenAlreadyDispatched() {
        PerLevelDataLoaderDispatchStrategy.CallStack callStack = strategy.initialCallStack;
        Set<Integer> dispatchedLevels = callStack.dispatchedLevels;

        // Set up state through the public API:
        // executionStrategy initializes level 0: executeObjectCalls=1, expectedFirstLevelFetchCount=1
        ExecutionStrategyParameters rootParams = paramsAtLevel(0);
        strategy.executionStrategy(executionContext, rootParams, 1);

        // fieldFetched at level 1 dispatches level 1
        ExecutionStrategyParameters level1Params = paramsAtLevel(1);
        strategy.fieldFetched(executionContext, level1Params,
                (DataFetcher<Object>) env -> null, "value",
                (Supplier<DataFetchingEnvironment>) () -> null);

        assertTrue(dispatchedLevels.contains(1), "Level 1 should be dispatched after fieldFetched");

        // Set up level 0 state so isLevelReady(2) returns true:
        // need completionFinished == executeObjectCalls at level 0
        PerLevelDataLoaderDispatchStrategy.CallStack.StateForLevel state0 = callStack.get(0);
        PerLevelDataLoaderDispatchStrategy.CallStack.StateForLevel updated = state0.increaseHappenedCompletionFinishedCount();
        callStack.tryUpdateLevel(0, state0, updated);

        // First call: should return true (level 2 not yet dispatched)
        boolean firstResult = strategy.markLevelAsDispatchedIfReady(2, callStack);
        assertTrue(firstResult, "First dispatch of level 2 should succeed");
        assertTrue(dispatchedLevels.contains(2), "Level 2 should be in dispatchedLevels");

        // Second call: simulates another thread arriving — should return false
        boolean secondResult = strategy.markLevelAsDispatchedIfReady(2, callStack);
        assertFalse(secondResult, "Second dispatch of level 2 should return false (already dispatched)");
    }

    /**
     * Tests the concurrent race between two threads calling onCompletionFinished,
     * both trying to dispatch the same level.
     */
    @Test
    void concurrentOnCompletionFinished_racesToDispatchSameLevel() throws Exception {
        // Set up with executeObjectCalls=2 at level 0
        ExecutionStrategyParameters rootParams = paramsAtLevel(0);
        strategy.executionStrategy(executionContext, rootParams, 1);

        // Increment executeObjectCalls at level 0 from 1 to 2
        ExecutionStrategyParameters level0Params = paramsAtLevel(0);
        strategy.executeObject(executionContext, level0Params, 1);

        // Dispatch level 1 via fieldFetched
        ExecutionStrategyParameters level1Params = paramsAtLevel(1);
        strategy.fieldFetched(executionContext, level1Params,
                (DataFetcher<Object>) env -> null, "value",
                (Supplier<DataFetchingEnvironment>) () -> null);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable task = () -> {
            try {
                startLatch.await();
                strategy.executeObjectOnFieldValuesInfo(
                        Collections.emptyList(), level0Params);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        executor.submit(task);
        executor.submit(task);

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        Set<Integer> dispatchedLevels = strategy.initialCallStack.dispatchedLevels;
        assertTrue(dispatchedLevels.contains(2),
                "Level 2 should be dispatched after both completions");
    }

    /**
     * Tests executeObjectOnFieldValuesException — the error recovery path.
     */
    @Test
    void executeObjectOnFieldValuesException_callsOnCompletionFinished() {
        ExecutionStrategyParameters rootParams = paramsAtLevel(0);
        strategy.executionStrategy(executionContext, rootParams, 1);

        // Dispatch level 1 via fieldFetched
        ExecutionStrategyParameters level1Params = paramsAtLevel(1);
        strategy.fieldFetched(executionContext, level1Params,
                (DataFetcher<Object>) env -> null, "value",
                (Supplier<DataFetchingEnvironment>) () -> null);

        // Call the error handler
        ExecutionStrategyParameters level2Params = paramsAtLevel(2);
        strategy.executeObjectOnFieldValuesException(
                new RuntimeException("test error"), level2Params);

        // Verify completion count at level 2 was incremented
        PerLevelDataLoaderDispatchStrategy.CallStack.StateForLevel state =
                strategy.initialCallStack.get(2);
        assertTrue(state.happenedCompletionFinishedCount > 0,
                "completionFinished should have been incremented by the exception handler");
    }

    /**
     * Tests executionStrategyOnFieldValuesException — the error recovery path
     * at the top-level execution strategy.
     */
    @Test
    void executionStrategyOnFieldValuesException_callsOnCompletionFinished() {
        ExecutionStrategyParameters rootParams = paramsAtLevel(0);
        strategy.executionStrategy(executionContext, rootParams, 1);

        // Call the error handler at the root level
        strategy.executionStrategyOnFieldValuesException(
                new RuntimeException("test error"), rootParams);

        // Verify completion count at level 0 was incremented
        PerLevelDataLoaderDispatchStrategy.CallStack.StateForLevel state =
                strategy.initialCallStack.get(0);
        assertTrue(state.happenedCompletionFinishedCount > 0,
                "completionFinished should have been incremented by the exception handler");
    }
}
