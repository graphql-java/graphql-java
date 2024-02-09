package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.Internal;
import graphql.execution.FieldValueInfo;
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.ExecuteObjectInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import org.dataloader.DataLoaderRegistry;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * This approach uses field level tracking to achieve its aims of making the data loader more efficient
 */
@Internal
public class FieldLevelTrackingApproach {
    private final Supplier<DataLoaderRegistry> dataLoaderRegistrySupplier;
    private static class CallStack implements InstrumentationState {

        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();
        private final LevelMap expectedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();

        CallStack() {
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

        public void clearAndMarkCurrentLevelAsReady(int level) {
            expectedFetchCountPerLevel.clear();
            fetchCountPerLevel.clear();
            expectedStrategyCallsPerLevel.clear();
            happenedStrategyCallsPerLevel.clear();
            happenedOnFieldValueCallsPerLevel.clear();
            dispatchedLevels.clear();

            // make sure the level is ready
            expectedFetchCountPerLevel.increment(level, 1);
            expectedStrategyCallsPerLevel.increment(level, 1);
            happenedStrategyCallsPerLevel.increment(level, 1);
        }
    }

    public FieldLevelTrackingApproach(Supplier<DataLoaderRegistry> dataLoaderRegistrySupplier) {
        this.dataLoaderRegistrySupplier = dataLoaderRegistrySupplier;
    }

    public InstrumentationState createState() {
        return new CallStack();
    }

    ExecutionStrategyInstrumentationContext beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters, InstrumentationState rawState) {
        CallStack callStack = (CallStack) rawState;
        int curLevel = getCurrentLevel(parameters);
        increaseCallCounts(callStack, curLevel, parameters);

        return new ExecutionStrategyInstrumentationContext() {
            @Override
            public void onDispatched(CompletableFuture<ExecutionResult> result) {

            }

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {

            }

            @Override
            public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
                onFieldValuesInfoDispatchIfNeeded(callStack, fieldValueInfoList, curLevel);
            }

            @Override
            public void onFieldValuesException() {
                synchronized (callStack) {
                    callStack.increaseHappenedOnFieldValueCalls(curLevel);
                }
            }
        };
    }

    ExecuteObjectInstrumentationContext beginObjectResolution(InstrumentationExecutionStrategyParameters parameters, InstrumentationState rawState) {
        CallStack callStack = (CallStack) rawState;
        int curLevel = getCurrentLevel(parameters);
        increaseCallCounts(callStack, curLevel, parameters);

        return new ExecuteObjectInstrumentationContext() {

            @Override
            public void onDispatched(CompletableFuture<Map<String, Object>> result) {
            }

            @Override
            public void onCompleted(Map<String, Object> result, Throwable t) {
            }

            @Override
            public void onFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
                onFieldValuesInfoDispatchIfNeeded(callStack, fieldValueInfoList, curLevel);
            }

            @Override
            public void onFieldValuesException() {
                synchronized (callStack) {
                    callStack.increaseHappenedOnFieldValueCalls(curLevel);
                }
            }
        };
    }

    private int getCurrentLevel(InstrumentationExecutionStrategyParameters parameters) {
        ResultPath path = parameters.getExecutionStrategyParameters().getPath();
        return path.getLevel() + 1;
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static void increaseCallCounts(CallStack callStack, int curLevel, InstrumentationExecutionStrategyParameters parameters) {
        int fieldCount = parameters.getExecutionStrategyParameters().getFields().size();
        synchronized (callStack) {
            callStack.increaseExpectedFetchCount(curLevel, fieldCount);
            callStack.increaseHappenedStrategyCalls(curLevel);
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private void onFieldValuesInfoDispatchIfNeeded(CallStack callStack, List<FieldValueInfo> fieldValueInfoList, int curLevel) {
        boolean dispatchNeeded;
        synchronized (callStack) {
            dispatchNeeded = handleOnFieldValuesInfo(fieldValueInfoList, callStack, curLevel);
        }
        if (dispatchNeeded) {
            dispatch();
        }
    }

    //
    // thread safety: called with synchronised(callStack)
    //
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, CallStack callStack, int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedStrategyCalls = getCountForList(fieldValueInfos);
        callStack.increaseExpectedStrategyCalls(curLevel + 1, expectedStrategyCalls);
        return dispatchIfNeeded(callStack, curLevel + 1);
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


    public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters, InstrumentationState rawState) {
        CallStack callStack = (CallStack) rawState;
        ResultPath path = parameters.getEnvironment().getExecutionStepInfo().getPath();
        int level = path.getLevel();
        return new InstrumentationContext<Object>() {

            @Override
            public void onDispatched(CompletableFuture<Object> result) {
                boolean dispatchNeeded;
                synchronized (callStack) {
                    callStack.increaseFetchCount(level);
                    dispatchNeeded = dispatchIfNeeded(callStack, level);
                }
                if (dispatchNeeded) {
                    dispatch();
                }

            }

            @Override
            public void onCompleted(Object result, Throwable t) {
            }
        };
    }


    //
    // thread safety : called with synchronised(callStack)
    //
    private boolean dispatchIfNeeded(CallStack callStack, int level) {
        if (levelReady(callStack, level)) {
            return callStack.dispatchIfNotDispatchedBefore(level);
        }
        return false;
    }

    //
    // thread safety: called with synchronised(callStack)
    //
    private boolean levelReady(CallStack callStack, int level) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return callStack.allFetchesHappened(1);
        }
        if (levelReady(callStack, level - 1) && callStack.allOnFieldCallsHappened(level - 1)
                && callStack.allStrategyCallsHappened(level) && callStack.allFetchesHappened(level)) {
            return true;
        }
        return false;
    }

    void dispatch() {
        DataLoaderRegistry dataLoaderRegistry = getDataLoaderRegistry();
        dataLoaderRegistry.dispatchAll();
    }

    private DataLoaderRegistry getDataLoaderRegistry() {
        return dataLoaderRegistrySupplier.get();
    }
}