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
        increaseCallCounts(curLevel, parameters);
    }

    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        if (this.startedDeferredExecution.get()) {
            return;
        }
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;
        increaseCallCounts(curLevel, parameters);
    }

    @Override
    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList) {
        if (this.startedDeferredExecution.get()) {
            this.dispatch();
        }
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, 1);
    }

    @Override
    public void executionStrategyOnFieldValuesException(Throwable t) {
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(1)
        );
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

    @Override
    public void fieldFetched(ExecutionContext executionContext,
                             ExecutionStrategyParameters parameters,
                             DataFetcher<?> dataFetcher,
                             Object fetchedValue) {

        final boolean dispatchNeeded;

        if (parameters.getField().isDeferred() || this.startedDeferredExecution.get()) {
            this.startedDeferredExecution.set(true);
            dispatchNeeded = true;
        } else {
            int level = parameters.getPath().getLevel();
            dispatchNeeded = callStack.lock.callLocked(() -> {
                callStack.increaseFetchCount(level);
                return dispatchIfNeeded(level);
            });
        }

        if (dispatchNeeded) {
            dispatch();
        }

    }

    private void increaseCallCounts(int curLevel, ExecutionStrategyParameters parameters) {
        int nonDeferredFieldCount = (int) parameters.getFields().getSubFieldsList().stream()
                .filter(field -> !field.isDeferred())
                .count();

        callStack.lock.runLocked(() -> {
            callStack.increaseExpectedFetchCount(curLevel, nonDeferredFieldCount);
            callStack.increaseHappenedStrategyCalls(curLevel);
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

