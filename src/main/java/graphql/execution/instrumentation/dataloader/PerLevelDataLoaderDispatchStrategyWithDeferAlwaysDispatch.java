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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The execution of a query can be divided into 2 phases: first, the non-deferred fields are executed and only once
 * they are completely resolved, we start to execute the deferred fields.
 * The behavior of this Data Loader strategy is quite different during those 2 phases. During the execution of the
 * deferred fields the Data Loader will not attempt to dispatch in a optimal way. It will essentially dispatch for
 * every field fetched, which is quite ineffective.
 * This is the first iteration of the Data Loader strategy with support for @defer, and it will be improved in the
 * future.
 */
@Internal
public class PerLevelDataLoaderDispatchStrategyWithDeferAlwaysDispatch implements DataLoaderDispatchStrategy {

    private final CallStack callStack;
    private final ExecutionContext executionContext;

    /**
     * This flag is used to determine if we have started the deferred execution.
     * The value of this flag is set to true as soon as we identified that a deferred field is being executed, and then
     * the flag stays on that state for the remainder of the execution.
     */
    private final AtomicBoolean startedDeferredExecution = new AtomicBoolean(false);


    private static class CallStack {

        private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();
        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();

        private final LevelMap expectedExecuteObjectCallsPerLevel = new LevelMap();
        private final LevelMap happenedExecuteObjectCallsPerLevel = new LevelMap();

        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();

        public CallStack() {
            expectedExecuteObjectCallsPerLevel.set(1, 1);
        }

        void increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.increment(level, count);
        }

        void clearExpectedFetchCount() {
            expectedFetchCountPerLevel.clear();
        }

        void increaseFetchCount(int level) {
            fetchCountPerLevel.increment(level, 1);
        }

        void clearFetchCount() {
            fetchCountPerLevel.clear();
        }

        void increaseExpectedExecuteObjectCalls(int level, int count) {
            expectedExecuteObjectCallsPerLevel.increment(level, count);
        }

        void clearExpectedObjectCalls() {
            expectedExecuteObjectCallsPerLevel.clear();
        }

        void increaseHappenedExecuteObjectCalls(int level) {
            happenedExecuteObjectCallsPerLevel.increment(level, 1);
        }

        void clearHappenedExecuteObjectCalls() {
            happenedExecuteObjectCallsPerLevel.clear();
        }

        void increaseHappenedOnFieldValueCalls(int level) {
            happenedOnFieldValueCallsPerLevel.increment(level, 1);
        }

        void clearHappenedOnFieldValueCalls() {
            happenedOnFieldValueCallsPerLevel.clear();
        }

        boolean allExecuteObjectCallsHappened(int level) {
            return happenedExecuteObjectCallsPerLevel.get(level) == expectedExecuteObjectCallsPerLevel.get(level);
        }

        boolean allOnFieldCallsHappened(int level) {
            return happenedOnFieldValueCallsPerLevel.get(level) == expectedExecuteObjectCallsPerLevel.get(level);
        }

        boolean allFetchesHappened(int level) {
            return fetchCountPerLevel.get(level) == expectedFetchCountPerLevel.get(level);
        }

        void clearDispatchLevels() {
            dispatchedLevels.clear();
        }

        @Override
        public String toString() {
            return "CallStack{" +
                    "expectedFetchCountPerLevel=" + expectedFetchCountPerLevel +
                    ", fetchCountPerLevel=" + fetchCountPerLevel +
                    ", expectedExecuteObjectCallsPerLevel=" + expectedExecuteObjectCallsPerLevel +
                    ", happenedExecuteObjectCallsPerLevel=" + happenedExecuteObjectCallsPerLevel +
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

    public PerLevelDataLoaderDispatchStrategyWithDeferAlwaysDispatch(ExecutionContext executionContext) {
        this.callStack = new CallStack();
        this.executionContext = executionContext;
    }

    @Override
    public void executeDeferredOnFieldValueInfo(FieldValueInfo fieldValueInfo, ExecutionStrategyParameters executionStrategyParameters) {
        this.startedDeferredExecution.set(true);
    }

    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        if (this.startedDeferredExecution.get()) {
            return;
        }
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(curLevel, parameters);

    }

    @Override
    public void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        resetCallStack();
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(1, 1);
    }

    @Override
    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
        if (this.startedDeferredExecution.get()) {
            this.dispatch();
        }
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, 1);
    }

    public void executionStrategyOnFieldValuesException(Throwable t) {
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(1)
        );
    }


    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        if (this.startedDeferredExecution.get()) {
            return;
        }
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(curLevel, parameters);
    }



    @Override
    public void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        if (this.startedDeferredExecution.get()) {
            this.dispatch();
        }
        int curLevel = parameters.getPath().getLevel() + 1;
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel);
    }


    @Override
    public void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel() + 1;
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(curLevel)
        );
    }

    private void increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(int curLevel, ExecutionStrategyParameters parameters) {
        int nonDeferredFields = 0;
        for (MergedField field : parameters.getFields().getSubFieldsList()) {
            if (!field.isDeferred()) {
                nonDeferredFields++;
            }
        }
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(curLevel, nonDeferredFields);
    }

    private void increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(int curLevel, int fieldCount) {
        callStack.lock.runLocked(() -> {
            callStack.increaseHappenedExecuteObjectCalls(curLevel);
            callStack.increaseExpectedFetchCount(curLevel, fieldCount);
        });
    }

    private void resetCallStack() {
        callStack.lock.runLocked(() -> {
            callStack.clearDispatchLevels();
            callStack.clearExpectedObjectCalls();
            callStack.clearExpectedFetchCount();
            callStack.clearFetchCount();
            callStack.clearHappenedExecuteObjectCalls();
            callStack.clearHappenedOnFieldValueCalls();
            callStack.expectedExecuteObjectCallsPerLevel.set(1, 1);
        });
    }
    private void onFieldValuesInfoDispatchIfNeeded(List<FieldValueInfo> fieldValueInfoList, int curLevel) {
        boolean dispatchNeeded = callStack.lock.callLocked(() ->
                handleOnFieldValuesInfo(fieldValueInfoList, curLevel)
        );
        if (dispatchNeeded) {
            dispatch();
        }
    }

    //
    // thread safety: called with callStack.lock
    //
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedStrategyCalls = getObjectCountForList(fieldValueInfos);
        callStack.increaseExpectedExecuteObjectCalls(curLevel + 1, expectedStrategyCalls);
        return dispatchIfNeeded(curLevel + 1);
    }

    /**
     * the amount of (non nullable) objects that will require an execute object call
     */
    private int getObjectCountForList(List<FieldValueInfo> fieldValueInfos) {
        int result = 0;
        for (FieldValueInfo fieldValueInfo : fieldValueInfos) {
            if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.OBJECT) {
                result += 1;
            } else if (fieldValueInfo.getCompleteValueType() == FieldValueInfo.CompleteValueType.LIST) {
                result += getObjectCountForList(fieldValueInfo.getFieldValueInfos());
            }
        }
        return result;
    }


    @Override
    public void fieldFetched(ExecutionContext executionContext,
                             ExecutionStrategyParameters executionStrategyParameters,
                             DataFetcher<?> dataFetcher,
                             Object fetchedValue) {

        final boolean dispatchNeeded;

        if (executionStrategyParameters.getField().isDeferred() || this.startedDeferredExecution.get()) {
            this.startedDeferredExecution.set(true);
            dispatchNeeded = true;
        } else {
            int level = executionStrategyParameters.getPath().getLevel();
            dispatchNeeded = callStack.lock.callLocked(() -> {
                callStack.increaseFetchCount(level);
                return dispatchIfNeeded(level);
            });
        }

        if (dispatchNeeded) {
            dispatch();
        }

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
                && callStack.allExecuteObjectCallsHappened(level) && callStack.allFetchesHappened(level)) {

            return true;
        }
        return false;
    }

    void dispatch() {
        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        dataLoaderRegistry.dispatchAll();
    }

}

