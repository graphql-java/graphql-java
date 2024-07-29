package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.execution.MergedField;
import graphql.schema.DataFetcher;
import graphql.util.LockKit;
import org.dataloader.DataLoaderRegistry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Internal
public class PerLevelDataLoaderDispatchStrategyWithDeferLegacy implements DataLoaderDispatchStrategy {
    // ITERATIONS:
    // 1 - all defer fields should be ignored.
    //   - when we identify that a field is deferred, we should just dispatch. This will result in multiple dispatches, which is not optimal.

    // 2 - new call stacks for every defer block.

    private final CallStack callStack;
    private final ExecutionContext executionContext;

    // data fetchers state: 1) not called 2) called but not returned 3) called and resolve

    // TODO: add test for only a scalar being deferred

    private static class CallStack {

        private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();

        // expected data fetchers method invocations
        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        // actual data fetchers that were invoked and returned
        private final LevelMap fetchCountPerLevel = new LevelMap();

        // object stuff
        private final LevelMap expectedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedStrategyCallsPerLevel = new LevelMap();

        // data fetchers methods have returned (returned an actual value, which we can inspect - it is list, non-null, object, etc....)
        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final LevelMap expectedDeferredStrategyCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();
        private final Set<Integer> levelsWithDeferredFields = new LinkedHashSet<>();

        public CallStack() {
            expectedStrategyCallsPerLevel.set(1, 1);
        }

        void increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.increment(level, count);
        }

        void levelHasDeferredFields(int level) {
            levelsWithDeferredFields.add(level);
        }

        void increaseFetchCount(int level) {
            fetchCountPerLevel.increment(level, 1);
        }

        void increaseExpectedStrategyCalls(int level, int count) {
            expectedStrategyCallsPerLevel.increment(level, count);
        }

        void increaseExpectedDeferredStrategyCalls(int level, int count) {
            expectedDeferredStrategyCallsPerLevel.increment(level, count);
            levelsWithDeferredFields.add(level);
        }

        void increaseHappenedStrategyCalls(int level) {
            happenedStrategyCallsPerLevel.increment(level, 1);
        }

        void increaseHappenedOnFieldValueCalls(int level) {
            happenedOnFieldValueCallsPerLevel.increment(level, 1);
        }

        boolean allStrategyCallsHappened(int level) {
            return happenedStrategyCallsPerLevel.get(level) == expectedStrategyCallsPerLevel.get(level) + expectedDeferredStrategyCallsPerLevel.get(level);
        }

        boolean allOnFieldCallsHappened(int level) {
            return happenedOnFieldValueCallsPerLevel.get(level) == expectedStrategyCallsPerLevel.get(level) + expectedDeferredStrategyCallsPerLevel.get(level);
        }

        boolean allFetchesHappened(int level) {
            return fetchCountPerLevel.get(level) == expectedFetchCountPerLevel.get(level);
        }

        @Override
        public String toString() {
            return "CallStack{" +
                    "expectedFetchCountPerLevel=" + expectedFetchCountPerLevel +
                    ", fetchCountPerLevel=" + fetchCountPerLevel +
                    ", expectedStrategyCallsPerLevel=" + expectedStrategyCallsPerLevel +
                    ", happenedStrategyCallsPerLevel=" + happenedStrategyCallsPerLevel +
                    ", happenedOnFieldValueCallsPerLevel=" + happenedOnFieldValueCallsPerLevel +
                    ", dispatchedLevels" + dispatchedLevels +
                    '}';
        }


        public boolean dispatchIfNotDispatchedBefore(int level) {
            if (dispatchedLevels.contains(level)) {
                // Levels that have deferred fields can be dispatched multiple times.
                // For one, we don't want to wait until deferred fields are resolved to dispatch the initial response.
                // Also, multiple defer blocks in the same level should not have to wait for each other.
                if (this.levelsWithDeferredFields.contains(level) || this.levelsWithDeferredFields.contains(level - 1)) {
                    return true;
                }
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
                return false;
            }
            dispatchedLevels.add(level);
            return true;
        }
    }

    public PerLevelDataLoaderDispatchStrategyWithDeferLegacy(ExecutionContext executionContext) {
        this.callStack = new CallStack();
        this.executionContext = executionContext;
    }

    @Override
    public void executeDeferredOnFieldValueInfo(FieldValueInfo fieldValueInfo, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;

        onFieldValuesInfoDispatchIfNeeded(Collections.singletonList(fieldValueInfo), curLevel, true);
    }

    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;
        increaseCallCounts(curLevel, parameters);
    }

    @Override
    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel, false);
    }

    @Override
    public void executionStrategyOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(curLevel)
        );
    }


    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;
        increaseCallCounts(curLevel, parameters);
    }

    @Override
    public void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel, false);
    }


    @Override
    public void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(curLevel)
        );
    }

    @Override
    public void fieldFetched(ExecutionContext executionContext,
                             ExecutionStrategyParameters parameters,
                             DataFetcher<?> dataFetcher,
                             Object fetchedValue) {
        int level = parameters.getPath().getLevel();
        boolean isDeferred = parameters.getField().isDeferred();
        boolean dispatchNeeded = callStack.lock.callLocked(() -> {
            if (!isDeferred) {
                callStack.increaseFetchCount(level);
            }
            return dispatchIfNeeded(level);
        });
        if (dispatchNeeded) {
            dispatch();
        }

    }


    private void increaseCallCounts(int curLevel, ExecutionStrategyParameters parameters) {
        int fieldCount = parameters.getFields().size();

        int deferredFieldCount = (int) parameters.getFields().getSubFieldsList().stream()
                .filter(MergedField::isDeferred)
                .count();

        callStack.lock.runLocked(() -> {
            // Deferred fields should be accounted in the expected fetch count, otherwise they might block the dispatch.
            callStack.increaseExpectedFetchCount(curLevel, fieldCount - deferredFieldCount);
            callStack.levelHasDeferredFields(curLevel);
            callStack.increaseHappenedStrategyCalls(curLevel);
        });
    }

    private void onFieldValuesInfoDispatchIfNeeded(List<FieldValueInfo> fieldValueInfoList, int curLevel, boolean isDeferred) {
        boolean dispatchNeeded = callStack.lock.callLocked(() ->
                handleOnFieldValuesInfo(fieldValueInfoList, curLevel, isDeferred)
        );
        if (dispatchNeeded) {
            dispatch();
        }
    }

    //
    // thread safety: called with callStack.lock
    //
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, int curLevel, boolean isDeferred) {
        int expectedStrategyCalls = getCountForList(fieldValueInfos);
        if (isDeferred) {
            // Expected strategy/object calls that are deferred are tracked separately, to avoid blocking the dispatching
            // of non-deferred fields.
            callStack.increaseExpectedDeferredStrategyCalls(curLevel + 1, expectedStrategyCalls);
        } else {
            callStack.increaseExpectedStrategyCalls(curLevel + 1, expectedStrategyCalls);
            callStack.increaseHappenedOnFieldValueCalls(curLevel);
        }

        return dispatchIfNeeded(curLevel + 1);
    }

    private int getCountForList(List<FieldValueInfo> fieldValueInfos) {
        int result = 0;
        for (FieldValueInfo fieldValueInfo : fieldValueInfos) {
            if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                result += 1;
            } else if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                result += getCountForList(fieldValueInfo.getFieldValueInfos());
            }
        }
        return result;
    }


    //
    // thread safety : called with callStack.lock
    //
    private boolean dispatchIfNeeded(int level) {
        boolean ready = levelReady(level);
        if (ready) {
            return callStack.dispatchIfNotDispatchedBefore(level);
        }
        return false;
    }

    //
    // thread safety: called with callStack.lock
    //
    private boolean levelReady(int level) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return callStack.allFetchesHappened(1);
        }
        if (levelReady(level - 1) && callStack.allOnFieldCallsHappened(level - 1)
                && callStack.allStrategyCallsHappened(level) && callStack.allFetchesHappened(level)) {

            return true;
        }
        return false;
    }

    void dispatch() {
        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        dataLoaderRegistry.dispatchAll();
    }

}

