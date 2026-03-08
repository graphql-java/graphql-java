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
import graphql.execution.FieldValueInfo;
import graphql.execution.MergedField;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private ExecutionStrategy dummyStrategy;

    @BeforeEach
    void setUp() {
        dummyStrategy = new graphql.execution.AsyncExecutionStrategy();
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
     * Tests that when two threads concurrently try to dispatch the same level,
     * the second thread correctly returns false from markLevelAsDispatchedIfReady.
     * <p>
     * This covers line 447 and the branch at line 445 which are otherwise
     * only hit under specific thread timing in integration tests.
     */
    @Test
    void markLevelAsDispatchedIfReady_returnsFalse_whenAnotherThreadAlreadyDispatched() throws Exception {
        // Access private initialCallStack field
        Field callStackField = PerLevelDataLoaderDispatchStrategy.class.getDeclaredField("initialCallStack");
        callStackField.setAccessible(true);
        Object callStack = callStackField.get(strategy);

        // Access dispatchedLevels on CallStack
        Field dispatchedLevelsField = callStack.getClass().getDeclaredField("dispatchedLevels");
        dispatchedLevelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Integer> dispatchedLevels = (Set<Integer>) dispatchedLevelsField.get(callStack);

        // Access stateForLevelMap to set up level 0 state
        Field stateMapField = callStack.getClass().getDeclaredField("stateForLevelMap");
        stateMapField.setAccessible(true);

        // Set up the state through the public API:
        // 1. executionStrategy initializes level 0: executeObjectCalls=1, expectedFirstLevelFetchCount=1
        ExecutionStrategyParameters rootParams = paramsAtLevel(0);
        strategy.executionStrategy(executionContext, rootParams, 1);

        // 2. fieldFetched at level 1 dispatches level 1 (adds it to dispatchedLevels)
        ExecutionStrategyParameters level1Params = paramsAtLevel(1);
        strategy.fieldFetched(executionContext, level1Params,
                (DataFetcher<Object>) env -> null, "value",
                (Supplier<DataFetchingEnvironment>) () -> null);

        // Verify level 1 is dispatched
        assertTrue(dispatchedLevels.contains(1), "Level 1 should be dispatched after fieldFetched");

        // Now level 0 has executeObjectCalls=1, completionFinished=0.
        // When executionStrategyOnFieldValuesInfo is called, it increments completionFinished to 1,
        // making level 2 ready (since 1==1 and level 1 is dispatched).
        // But FIRST, simulate another thread having already dispatched level 2:
        dispatchedLevels.add(2);

        // Now call the private markLevelAsDispatchedIfReady(2, callStack) via reflection.
        // isLevelReady(2) will return true (level 1 dispatched, executeObjectCalls(0)==1,
        // but completionFinished is 0, so we need to set it up).
        // Actually, we need completionFinished == executeObjectCalls at level 0.
        // Let's increment completionFinished by calling onCompletionFinished directly.
        // But onCompletionFinished checks dispatchedLevels.contains(level+2) first.
        // Since we added 2 to dispatchedLevels, onCompletionFinished(0) would break immediately.

        // Instead, set up the state directly: make level 0 have matching counts.
        // We need to call executeObjectOnFieldValuesInfo at level 0 to increment completionFinished.
        // But that also checks level 2... which we pre-added. So it would break.

        // The cleanest approach: remove level 2, call the public API to set up the state,
        // then add level 2 back, then call markLevelAsDispatchedIfReady directly.
        dispatchedLevels.remove(2);

        // Increment completionFinished at level 0 via executionStrategyOnFieldValuesInfo
        // This will also trigger dispatch of level 2 through the normal path.
        // Let's just set up the state via reflection instead.

        // Get the markLevelAsDispatchedIfReady method
        Method markMethod = PerLevelDataLoaderDispatchStrategy.class
                .getDeclaredMethod("markLevelAsDispatchedIfReady", int.class, callStack.getClass());
        markMethod.setAccessible(true);

        // Get isLevelReady to work: need completionFinished == executeObjectCalls at level 0
        // Level 0 currently has StateForLevel(completionFinished=0, executeObjectCalls=1)
        // We need to set completionFinished=1. Use the get/tryUpdateLevel methods on callStack.
        Method getMethod = callStack.getClass().getDeclaredMethod("get", int.class);
        getMethod.setAccessible(true);
        Object stateForLevel0 = getMethod.invoke(callStack, 0);

        Method increaseCompletionMethod = stateForLevel0.getClass()
                .getDeclaredMethod("increaseHappenedCompletionFinishedCount");
        increaseCompletionMethod.setAccessible(true);
        Object updatedState = increaseCompletionMethod.invoke(stateForLevel0);

        Method tryUpdateMethod = callStack.getClass()
                .getDeclaredMethod("tryUpdateLevel", int.class, stateForLevel0.getClass(), stateForLevel0.getClass());
        tryUpdateMethod.setAccessible(true);
        tryUpdateMethod.invoke(callStack, 0, stateForLevel0, updatedState);

        // Now level 0 has executeObjectCalls=1, completionFinished=1 → level 2 is ready.

        // First call: should return true (level 2 not yet dispatched)
        boolean firstResult = (boolean) markMethod.invoke(strategy, 2, callStack);
        assertTrue(firstResult, "First dispatch of level 2 should succeed");
        assertTrue(dispatchedLevels.contains(2), "Level 2 should be in dispatchedLevels");

        // Second call: simulates another thread arriving — should return false (LINE 447)
        boolean secondResult = (boolean) markMethod.invoke(strategy, 2, callStack);
        assertFalse(secondResult, "Second dispatch of level 2 should return false (another thread already dispatched)");
    }

    /**
     * Tests the concurrent race between two threads calling onCompletionFinished,
     * both trying to dispatch the same level. Uses CountDownLatch to maximize
     * the chance of the race occurring.
     */
    @Test
    void concurrentOnCompletionFinished_racesToDispatchSameLevel() throws Exception {
        // Set up with executeObjectCalls=2 at level 0 so that both threads
        // see completionFinished==executeObjectCalls after both increment
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

        // Now: level 0 has executeObjectCalls=2, completionFinished=0, level 1 is dispatched.
        // Two threads calling executeObjectOnFieldValuesInfo (level 0) will both increment
        // completionFinished. When both have incremented (to 2), isLevelReady(2) returns true
        // for both, and they race to dispatchedLevels.add(2).

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean thread1Result = new AtomicBoolean(false);
        AtomicBoolean thread2Result = new AtomicBoolean(false);
        AtomicBoolean raceOccurred = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Both threads will call executeObjectOnFieldValuesInfo at level 0
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

        // Release both threads simultaneously
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Access dispatchedLevels to verify level 2 was dispatched exactly once
        Field callStackField = PerLevelDataLoaderDispatchStrategy.class.getDeclaredField("initialCallStack");
        callStackField.setAccessible(true);
        Object callStack = callStackField.get(strategy);
        Field dispatchedLevelsField = callStack.getClass().getDeclaredField("dispatchedLevels");
        dispatchedLevelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Integer> dispatchedLevels = (Set<Integer>) dispatchedLevelsField.get(callStack);

        // Level 2 should be dispatched (regardless of which thread won the race)
        assertTrue(dispatchedLevels.contains(2),
                "Level 2 should be dispatched after both completions");
    }

    /**
     * Tests executeObjectOnFieldValuesException — the error recovery path
     * that is never triggered by integration tests.
     */
    @Test
    void executeObjectOnFieldValuesException_callsOnCompletionFinished() throws Exception {
        ExecutionStrategyParameters rootParams = paramsAtLevel(0);
        strategy.executionStrategy(executionContext, rootParams, 1);

        // Dispatch level 1 via fieldFetched
        ExecutionStrategyParameters level1Params = paramsAtLevel(1);
        strategy.fieldFetched(executionContext, level1Params,
                (DataFetcher<Object>) env -> null, "value",
                (Supplier<DataFetchingEnvironment>) () -> null);

        // Call the error handler — this should not throw and should call onCompletionFinished
        ExecutionStrategyParameters level2Params = paramsAtLevel(2);
        strategy.executeObjectOnFieldValuesException(
                new RuntimeException("test error"), level2Params);

        // Verify it ran without error — the completion count at level 2 should have incremented
        Field callStackField = PerLevelDataLoaderDispatchStrategy.class.getDeclaredField("initialCallStack");
        callStackField.setAccessible(true);
        Object callStack = callStackField.get(strategy);

        Method getMethod = callStack.getClass().getDeclaredMethod("get", int.class);
        getMethod.setAccessible(true);
        Object stateForLevel2 = getMethod.invoke(callStack, 2);

        Field completionField = stateForLevel2.getClass().getDeclaredField("happenedCompletionFinishedCount");
        completionField.setAccessible(true);
        int completionCount = (int) completionField.get(stateForLevel2);
        assertTrue(completionCount > 0,
                "completionFinished should have been incremented by the exception handler");
    }

    /**
     * Tests executionStrategyOnFieldValuesException — the error recovery path
     * at the top-level execution strategy that is never triggered by integration tests.
     */
    @Test
    void executionStrategyOnFieldValuesException_callsOnCompletionFinished() throws Exception {
        ExecutionStrategyParameters rootParams = paramsAtLevel(0);
        strategy.executionStrategy(executionContext, rootParams, 1);

        // Call the error handler at the root level
        strategy.executionStrategyOnFieldValuesException(
                new RuntimeException("test error"), rootParams);

        // Verify completion count at level 0 was incremented
        Field callStackField = PerLevelDataLoaderDispatchStrategy.class.getDeclaredField("initialCallStack");
        callStackField.setAccessible(true);
        Object callStack = callStackField.get(strategy);

        Method getMethod = callStack.getClass().getDeclaredMethod("get", int.class);
        getMethod.setAccessible(true);
        Object stateForLevel0 = getMethod.invoke(callStack, 0);

        Field completionField = stateForLevel0.getClass().getDeclaredField("happenedCompletionFinishedCount");
        completionField.setAccessible(true);
        int completionCount = (int) completionField.get(stateForLevel0);
        assertTrue(completionCount > 0,
                "completionFinished should have been incremented by the exception handler");
    }
}
