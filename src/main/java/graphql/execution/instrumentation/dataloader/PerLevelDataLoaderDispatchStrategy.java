package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.Execution;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.util.LockKit;
import org.dataloader.DataLoaderRegistry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Internal
public class PerLevelDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final CallStack callStack;
    private final ExecutionContext executionContext;

    static final ScheduledExecutorService isolatedDLCFBatchWindowScheduler = Executors.newSingleThreadScheduledExecutor();
    static final int BATCH_WINDOW_NANO_SECONDS = 100_000;


    private static class CallStack {

        private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();
        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();

        private final LevelMap expectedExecuteObjectCallsPerLevel = new LevelMap();
        private final LevelMap happenedExecuteObjectCallsPerLevel = new LevelMap();

        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();

        // fields only relevant when a DataLoaderCF is involved
        private final List<DataLoaderCF<?>> allDataLoaderCF = new CopyOnWriteArrayList<>();
        //TODO: maybe this should be cleaned up once the CF returned by these fields are completed
        // otherwise this will stick around until the whole request is finished
        private final Set<DataFetchingEnvironment> fieldsFinishedDispatching = ConcurrentHashMap.newKeySet();
        private final Map<Integer, Set<DataFetchingEnvironment>> levelToDFEWithDataLoaderCF = new ConcurrentHashMap<>();

        private final Set<DataFetchingEnvironment> batchWindowOfIsolatedDfeToDispatch = ConcurrentHashMap.newKeySet();

        private boolean batchWindowOpen = false;


        public CallStack() {
            expectedExecuteObjectCallsPerLevel.set(1, 1);
        }

        public void addDataLoaderDFE(int level, DataFetchingEnvironment dfe) {
            levelToDFEWithDataLoaderCF.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(dfe);
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

    public PerLevelDataLoaderDispatchStrategy(ExecutionContext executionContext) {
        this.callStack = new CallStack();
        this.executionContext = executionContext;
    }

    @Override
    public void executeDeferredOnFieldValueInfo(FieldValueInfo fieldValueInfo, ExecutionStrategyParameters executionStrategyParameters) {
        throw new UnsupportedOperationException("Data Loaders cannot be used to resolve deferred fields");
    }

    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
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
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, 1);
    }

    public void executionStrategyOnFieldValuesException(Throwable t) {
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(1)
        );
    }


    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getExecutionStepInfo().getPath().getLevel() + 1;
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(curLevel, parameters);
    }

    @Override
    public void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
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

    private void increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(int curLevel, ExecutionStrategyParameters executionStrategyParameters) {
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(curLevel, executionStrategyParameters.getFields().size());
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
            dispatch(curLevel);
        }
    }

    //
// thread safety: called with callStack.lock
//
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedOnObjectCalls = getObjectCountForList(fieldValueInfos);
        callStack.increaseExpectedExecuteObjectCalls(curLevel + 1, expectedOnObjectCalls);
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
                             Object fetchedValue,
                             Supplier<DataFetchingEnvironment> dataFetchingEnvironment) {
        int level = executionStrategyParameters.getPath().getLevel();
        boolean dispatchNeeded = callStack.lock.callLocked(() -> {
            if (DataLoaderCF.isDataLoaderCF(fetchedValue)) {
                callStack.addDataLoaderDFE(level, dataFetchingEnvironment.get());
            }
            callStack.increaseFetchCount(level);
            return dispatchIfNeeded(level);
        });
        if (dispatchNeeded) {
            dispatch(level);
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

    void dispatch(int level) {
        // only dispatch if we don't use any DataLoaderCFs
        if (callStack.levelToDFEWithDataLoaderCF.size() == 0) {
            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            dataLoaderRegistry.dispatchAll();
        }
    }


    public void dispatchDLCFImpl(Set<DataFetchingEnvironment> dfeToDispatchSet) {

        // filter out all DataLoaderCFS that are matching the fields we want to dispatch
        List<DataLoaderCF<?>> relevantDataLoaderCFs = new ArrayList<>();
        List<CompletableFuture<Void>> finishedSyncDependentsCFs = new ArrayList<>();
        for (DataLoaderCF<?> dataLoaderCF : callStack.allDataLoaderCF) {
            if (dfeToDispatchSet.contains(dataLoaderCF.dfe)) {
                relevantDataLoaderCFs.add(dataLoaderCF);
                finishedSyncDependentsCFs.add(dataLoaderCF.finishedSyncDependents);
            }
        }
        // we are cleaning up the list of all DataLoadersCFs
        callStack.allDataLoaderCF.removeAll(relevantDataLoaderCFs);

        // means we are all done dispatching the fields
        if (relevantDataLoaderCFs.size() == 0) {
            callStack.fieldsFinishedDispatching.addAll(dfeToDispatchSet);
            return;
        }
        // we are dispatching all data loaders and waiting for all dataLoaderCFs to complete
        // and to finish their sync actions

        CompletableFuture
                .allOf(finishedSyncDependentsCFs.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) ->
                        dispatchDLCFImpl(dfeToDispatchSet)
                );
        // Only dispatching relevant data loaders
        for (DataLoaderCF dlCF : relevantDataLoaderCFs) {
            dlCF.dfe.getDataLoader(dlCF.dataLoaderName).dispatch();
        }
//        executionContext.getDataLoaderRegistry().dispatchAll();
    }


    public void newDataLoaderCF(DataLoaderCF<?> dataLoaderCF) {
        System.out.println("newDataLoaderCF");
        callStack.lock.runLocked(() -> {
            callStack.allDataLoaderCF.add(dataLoaderCF);
        });
//        if (callStack.fieldsFinishedDispatching.contains(dataLoaderCF.dfe)) {
        System.out.println("isolated dispatch");
        dispatchIsolatedDataLoader(dataLoaderCF);
//        }

    }

    class TriggerDispatch implements Runnable {

        final ExecutionContext executionContext;

        TriggerDispatch(ExecutionContext executionContext) {
            this.executionContext = executionContext;
        }

        @Override
        public void run() {
            if (executionContext.isRunning()) {
                isolatedDLCFBatchWindowScheduler.schedule(this, BATCH_WINDOW_NANO_SECONDS, TimeUnit.NANOSECONDS);
                return;
            }
            AtomicReference<Set<DataFetchingEnvironment>> dfesToDispatch = new AtomicReference<>();
            callStack.lock.runLocked(() -> {
                dfesToDispatch.set(new LinkedHashSet<>(callStack.batchWindowOfIsolatedDfeToDispatch));
                callStack.batchWindowOfIsolatedDfeToDispatch.clear();
                callStack.batchWindowOpen = false;
            });
            dispatchDLCFImpl(dfesToDispatch.get());
        }
    }

    private void dispatchIsolatedDataLoader(DataLoaderCF<?> dlCF) {
        callStack.lock.runLocked(() -> {
            callStack.batchWindowOfIsolatedDfeToDispatch.add(dlCF.dfe);
            ExecutionContext executionContext = dlCF.dfe.getGraphQlContext().get(Execution.EXECUTION_CONTEXT_KEY);
            if (!callStack.batchWindowOpen) {
                callStack.batchWindowOpen = true;
                isolatedDLCFBatchWindowScheduler.schedule(new TriggerDispatch(executionContext), BATCH_WINDOW_NANO_SECONDS, TimeUnit.NANOSECONDS);
            }

        });
    }


}

