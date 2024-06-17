package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.schema.DataFetcher;
import graphql.util.LockKit;
import org.dataloader.DataLoaderRegistry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Internal
public class PerLevelDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final CallStack callStack;
    private final ExecutionContext executionContext;


    private static class CallStack {

        private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();
        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();
        private final LevelMap expectedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final LevelMap expectedDeferredStrategyCallsPerLevel = new LevelMap();
        private final LevelMap happenedOnDeferredFieldValueCallsPerLevel = new LevelMap();

        private final AtomicBoolean hasDeferredCalls = new AtomicBoolean(false);

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

        void increaseExpectedDeferredStrategyCalls(int level, int count) {
            expectedDeferredStrategyCallsPerLevel.increment(level, count);
            hasDeferredCalls.set(true);
        }

        void increaseHappenedStrategyCalls(int level) {
            happenedStrategyCallsPerLevel.increment(level, 1);
        }

        void increaseHappenedOnFieldValueCalls(int level) {
            happenedOnFieldValueCallsPerLevel.increment(level, 1);
        }

        void increaseHappenedOnDeferredFieldValueCalls(int level) {
            happenedOnDeferredFieldValueCallsPerLevel.increment(level, 1);
            hasDeferredCalls.set(true);
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

        private boolean hasDeferredCalls() {
            return hasDeferredCalls.get();
        }

        private boolean hasDeferredCalls(int level) {
            return expectedDeferredStrategyCallsPerLevel.get(level) > 0 || happenedOnDeferredFieldValueCallsPerLevel.get(level) > 0;
        }

        @Override
        public String toString() {
            return "{" +
                    "efc=" + expectedFetchCountPerLevel +
                    ", fc=" + fetchCountPerLevel +
                    ", esc=" + expectedStrategyCallsPerLevel +
                    ", hsc=" + happenedStrategyCallsPerLevel +
                    ", hofvc=" + happenedOnFieldValueCallsPerLevel +
                    ", de=" + expectedDeferredStrategyCallsPerLevel +
                    ", dh=" + happenedOnDeferredFieldValueCallsPerLevel +
                    ", dl" + dispatchedLevels +
                    '}';
        }


        public boolean dispatchIfNotDispatchedBefore(int level, String origin) {
            if (dispatchedLevels.contains(level)) {
                if(this.hasDeferredCalls(level - 1)) {
                    System.out.println("df: " + level + " already dispatched.");
                    return true;
                }
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
    public void deferredField(FieldValueInfo fieldValueInfo, ExecutionStrategyParameters executionStrategyParameters) {
        int curLevel = executionStrategyParameters.getExecutionStepInfo().getPath().getLevel() + 1;

        boolean dispatchNeeded = callStack.lock.callLocked(() -> {
                    callStack.increaseHappenedOnDeferredFieldValueCalls(curLevel);

                    int expectedStrategyCalls = getCountForList(Collections.singletonList(fieldValueInfo));
                    callStack.increaseExpectedDeferredStrategyCalls(curLevel + 1, expectedStrategyCalls);

//                    callStack.increaseExpectedStrategyCalls(curLevel + 1, expectedStrategyCalls);

                    System.out.println(
                            "df: " + curLevel + " :: " +
                                    callStack + " :: " +
                                    executionStrategyParameters.getPath() + " :: "
                    );

//                    return false;
                    return dispatchIfNeeded(curLevel + 1, "df");
                }
        );
        if (dispatchNeeded) {
            dispatch(curLevel);
        }
    }

    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;

        increaseCallCounts(curLevel, parameters, "st");
    }

    @Override
    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;

        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel, parameters, "ev");
    }

    @Override
    public void executionStrategyOnFieldValuesException(Throwable t, ExecutionStrategyParameters executionStrategyParameters) {
        int curLevel = executionStrategyParameters.getPath().getLevel() + 1;
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(curLevel)
        );
    }


    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;

        increaseCallCounts(curLevel, parameters, "ob");
    }

    @Override
    public void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;

//        System.out.println(
//                "ef: " + curLevel + " :: " +
//                        callStack + " :: " +
//                        parameters.getPath()
//        );

        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel, parameters, "of");
    }


    @Override
    public void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(curLevel)
        );
    }


    private void increaseCallCounts(int curLevel, ExecutionStrategyParameters executionStrategyParameters, String origin) {
        int fieldCount = executionStrategyParameters.getFields().size();
        callStack.lock.runLocked(() -> {
            callStack.increaseExpectedFetchCount(curLevel, fieldCount);
            callStack.increaseHappenedStrategyCalls(curLevel);
        });

        System.out.println(
                origin + ": " + curLevel + " :: " +
                        callStack + " :: " +
                        executionStrategyParameters.getPath()
        );
    }

    private void onFieldValuesInfoDispatchIfNeeded(List<FieldValueInfo> fieldValueInfoList, int curLevel, ExecutionStrategyParameters parameters, String origin) {
        boolean dispatchNeeded = callStack.lock.callLocked(() ->
                handleOnFieldValuesInfo(fieldValueInfoList, curLevel, origin, parameters)
        );
        if (dispatchNeeded) {
            dispatch(curLevel);
        }
    }

    //
    // thread safety: called with callStack.lock
    //
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, int curLevel, String origin, ExecutionStrategyParameters parameters) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedStrategyCalls = getCountForList(fieldValueInfos);
        callStack.increaseExpectedStrategyCalls(curLevel + 1, expectedStrategyCalls);

        System.out.println(
                origin + ": " + curLevel + " :: " +
                        callStack + " :: " +
                        parameters.getPath() + " :: "
        );

        return dispatchIfNeeded(curLevel + 1, origin);
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
    public void fieldFetched(ExecutionContext executionContext,
                             ExecutionStrategyParameters executionStrategyParameters,
                             DataFetcher<?> dataFetcher,
                             Object fetchedValue) {
        int level = executionStrategyParameters.getPath().getLevel();
        boolean dispatchNeeded = callStack.lock.callLocked(() -> {
            callStack.increaseFetchCount(level);
            return dispatchIfNeeded(level, "ff");
        });

        System.out.println(
                "ff: " + level + " :: " +
                        callStack + " :: " +
                        executionStrategyParameters.getPath() + " :: " +
                        fetchedValue
        );

        if (dispatchNeeded) {
            dispatch(level);
        }

    }


    //
// thread safety : called with callStack.lock
//
    private boolean dispatchIfNeeded(int level, String origin) {
        boolean ready = levelReady(level, origin);
        if (ready) {
            System.out.println(level + " dispatched :: "  + origin);
            return callStack.dispatchIfNotDispatchedBefore(level, origin);
        }
        return false;
    }

    //
    // thread safety: called with callStack.lock
    //
    private boolean levelReady(int level, String origin) {
        if (level == 1) {
            // level 1 is special: there is only one strategy call and that's it
            return callStack.allFetchesHappened(1);
        }


        boolean lr = levelReady(level - 1, "-1");
        boolean aofch = callStack.allOnFieldCallsHappened(level - 1);
        boolean atch = callStack.allStrategyCallsHappened(level);
        boolean afh = callStack.allFetchesHappened(level);

        if (lr && aofch && atch && afh) {
            return true;
        }
        return false;
    }

    void dispatch(int level) {
        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        dataLoaderRegistry.dispatchAll();
    }

}

