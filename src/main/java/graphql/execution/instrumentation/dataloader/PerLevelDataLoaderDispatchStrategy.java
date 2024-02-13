package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.schema.DataFetcher;
import org.dataloader.DataLoaderRegistry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Internal
public class PerLevelDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final CallStack callStack;
    private final ExecutionContext executionContext;

    private static class CallStack {
        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();
        private final LevelMap expectedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();

        public CallStack() {
            expectedStrategyCallsPerLevel.set(1, 1);
        }

        void increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.increment(level, count);
        }

        void increaseFetchCount(int level) {
            fetchCountPerLevel.increment(level, 1);
        }

        void increaseExpectedStrategyCalls(int level, int count) {
            expectedStrategyCallsPerLevel.increment(level, count);
        }

        void increaseHappenedStrategyCalls(int level) {
            happenedStrategyCallsPerLevel.increment(level, 1);
        }

        void increaseHappenedOnFieldValueCalls(int level) {
            happenedOnFieldValueCallsPerLevel.increment(level, 1);
        }

        boolean allStrategyCallsHappened(int level) {
            return happenedStrategyCallsPerLevel.get(level) == expectedStrategyCallsPerLevel.get(level);
        }

        boolean allOnFieldCallsHappened(int level) {
            return happenedOnFieldValueCallsPerLevel.get(level) == expectedStrategyCallsPerLevel.get(level);
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
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
                return false;
            }
            dispatchedLevels.add(level);
            return true;
        }
    }

    public PerLevelDataLoaderDispatchStrategy(ExecutionContext executionContext) {
        this.callStack = new CallStack();
        this.executionContext = executionContext;
    }

    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;
        increaseCallCounts(curLevel, parameters);
    }

    @Override
    public void executionStrategy_onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel);
    }

    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;
        increaseCallCounts(curLevel, parameters);
    }

    @Override
    public void executeObject_onFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        synchronized (callStack) {
            callStack.increaseHappenedOnFieldValueCalls(curLevel);
        }
    }


    @Override
    public void executionStrategy_onFieldValuesException(Throwable t, ExecutionStrategyParameters executionStrategyParameters) {
        int curLevel = executionStrategyParameters.getPath().getLevel() + 1;
        synchronized (callStack) {
            callStack.increaseHappenedOnFieldValueCalls(curLevel);
        }

    }

    @Override
    public void executeObject_onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private void increaseCallCounts(int curLevel, ExecutionStrategyParameters executionStrategyParameters) {
        int fieldCount = executionStrategyParameters.getFields().size();
        synchronized (callStack) {
            callStack.increaseExpectedFetchCount(curLevel, fieldCount);
            callStack.increaseHappenedStrategyCalls(curLevel);
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private void onFieldValuesInfoDispatchIfNeeded(List<FieldValueInfo> fieldValueInfoList, int curLevel) {
        boolean dispatchNeeded;
        synchronized (callStack) {
            dispatchNeeded = handleOnFieldValuesInfo(fieldValueInfoList, curLevel);
        }
        if (dispatchNeeded) {
            dispatch(curLevel);
        }
    }

    //
// thread safety: called with synchronised(callStack)
//
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedStrategyCalls = getCountForList(fieldValueInfos);
        callStack.increaseExpectedStrategyCalls(curLevel + 1, expectedStrategyCalls);
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


    @Override
    public void fieldFetched(ExecutionContext executionContext, ExecutionStrategyParameters executionStrategyParameters, DataFetcher<?> dataFetcher, CompletableFuture<Object> fetchedValue) {
        int level = executionStrategyParameters.getPath().getLevel();
        boolean dispatchNeeded;
        synchronized (callStack) {
            callStack.increaseFetchCount(level);
            dispatchNeeded = dispatchIfNeeded(level);
        }
        if (dispatchNeeded) {
            dispatch(level);
        }

    }


    //
// thread safety : called with synchronised(callStack)
//
    private boolean dispatchIfNeeded(int level) {
        boolean ready = levelReady(level);
        if (ready) {
            return callStack.dispatchIfNotDispatchedBefore(level);
        }
        return false;
    }

    //
// thread safety: called with synchronised(callStack)
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

    void dispatch(int level) {
        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        dataLoaderRegistry.dispatchAll();
    }

}

