package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.util.InterThreadMemoizedSupplier;
import graphql.util.LockKit;
import org.dataloader.DataLoader;
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
import java.util.stream.Collectors;

@Internal
public class PerLevelDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final CallStack callStack;
    private final ExecutionContext executionContext;
    private final int batchWindowNs;

    private final InterThreadMemoizedSupplier<ScheduledExecutorService> delayedDataLoaderDispatchExecutor;

    static final InterThreadMemoizedSupplier<ScheduledExecutorService> defaultDelayedDLCFBatchWindowScheduler
            = new InterThreadMemoizedSupplier<>(Executors::newSingleThreadScheduledExecutor);

    static final int DEFAULT_BATCH_WINDOW_NANO_SECONDS_DEFAULT = 500_000;


    private static class CallStack {

        private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();
        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();

        private final LevelMap expectedExecuteObjectCallsPerLevel = new LevelMap();
        private final LevelMap happenedExecuteObjectCallsPerLevel = new LevelMap();

        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = ConcurrentHashMap.newKeySet();

        // fields only relevant when a DataLoaderCF is involved
        private final List<ResultPathWithDataLoader> allResultPathWithDataLoader = new CopyOnWriteArrayList<>();

        //TODO: maybe this should be cleaned up once the CF returned by these fields are completed
        // otherwise this will stick around until the whole request is finished
        private final Map<Integer, Set<ResultPathWithDataLoader>> levelToResultPathWithDataLoader = new ConcurrentHashMap<>();
        private final Set<String> batchWindowOfDelayedDataLoaderToDispatch = ConcurrentHashMap.newKeySet();

        private boolean batchWindowOpen = false;


        public CallStack() {
            expectedExecuteObjectCallsPerLevel.set(1, 1);
        }

        public void addDataLoaderDFE(int level, ResultPathWithDataLoader resultPathWithDataLoader) {
            levelToResultPathWithDataLoader.computeIfAbsent(level, k -> new LinkedHashSet<>()).add(resultPathWithDataLoader);
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

        Integer batchWindowNs = executionContext.getGraphQLContext().get(DispatchingContextKeys.DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS);
        if (batchWindowNs != null) {
            this.batchWindowNs = batchWindowNs;
        } else {
            this.batchWindowNs = DEFAULT_BATCH_WINDOW_NANO_SECONDS_DEFAULT;
        }

        this.delayedDataLoaderDispatchExecutor = new InterThreadMemoizedSupplier<>(() -> {
            DelayedDataLoaderDispatcherExecutorFactory delayedDataLoaderDispatcherExecutorFactory = executionContext.getGraphQLContext().get(DispatchingContextKeys.DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY);
            if (delayedDataLoaderDispatcherExecutorFactory != null) {
                return delayedDataLoaderDispatcherExecutorFactory.createExecutor(executionContext.getExecutionId(), executionContext.getGraphQLContext());
            }
            return defaultDelayedDLCFBatchWindowScheduler.get();
        });
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
        // the handle on field values check for the next level if it is ready
        if (dispatchNeeded) {
            dispatch(curLevel + 1);
        }
    }

    //
// thread safety: called with callStack.lock
//
    private boolean handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedOnObjectCalls = getObjectCountForList(fieldValueInfos);
        // on the next level we expect the following on object calls because we found non null objects
        callStack.increaseExpectedExecuteObjectCalls(curLevel + 1, expectedOnObjectCalls);
        // maybe the object calls happened already (because the DataFetcher return directly values synchronously)
        // therefore we check if the next level is ready
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
        // if we have any DataLoaderCFs => use new Algorithm
        Set<ResultPathWithDataLoader> resultPathWithDataLoaders = callStack.levelToResultPathWithDataLoader.get(level);
        if (resultPathWithDataLoaders != null) {
            dispatchDLCFImpl(resultPathWithDataLoaders
                    .stream()
                    .map(resultPathWithDataLoader -> resultPathWithDataLoader.resultPath)
                    .collect(Collectors.toSet()), true);
        } else {
            // otherwise dispatch all DataLoaders
            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            dataLoaderRegistry.dispatchAll();
        }
    }


    public void dispatchDLCFImpl(Set<String> resultPathsToDispatch, boolean dispatchAll) {

        // filter out all DataLoaderCFS that are matching the fields we want to dispatch
        List<ResultPathWithDataLoader> relevantResultPathWithDataLoader = new ArrayList<>();
        for (ResultPathWithDataLoader resultPathWithDataLoader : callStack.allResultPathWithDataLoader) {
            if (resultPathsToDispatch.contains(resultPathWithDataLoader.resultPath)) {
                relevantResultPathWithDataLoader.add(resultPathWithDataLoader);
            }
        }
        // we are cleaning up the list of all DataLoadersCFs
        callStack.allResultPathWithDataLoader.removeAll(relevantResultPathWithDataLoader);

        // means we are all done dispatching the fields
        if (relevantResultPathWithDataLoader.size() == 0) {
            return;
        }
        List<CompletableFuture> allDispatchedCFs = new ArrayList<>();
        if (dispatchAll) {
//         if we have a mixed world with old and new DataLoaderCFs we dispatch all DataLoaders to retain compatibility
            for (DataLoader dl : executionContext.getDataLoaderRegistry().getDataLoaders()) {
                allDispatchedCFs.add(dl.dispatch());
            }
        } else {
            // Only dispatching relevant data loaders
            for (ResultPathWithDataLoader resultPathWithDataLoader : relevantResultPathWithDataLoader) {
                allDispatchedCFs.add(resultPathWithDataLoader.dataLoader.dispatch());
            }
        }
        CompletableFuture.allOf(allDispatchedCFs.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) -> {
                    dispatchDLCFImpl(resultPathsToDispatch, false);
                        }
                );

    }


    public void newDataLoaderCF(String resultPath, int level, DataLoader dataLoader) {
        ResultPathWithDataLoader resultPathWithDataLoader = new ResultPathWithDataLoader(resultPath, level, dataLoader);
        callStack.lock.runLocked(() -> {
            callStack.allResultPathWithDataLoader.add(resultPathWithDataLoader);
            if (!callStack.dispatchedLevels.contains(level)) {
                callStack.addDataLoaderDFE(level, resultPathWithDataLoader);
            }
        });
        if (callStack.dispatchedLevels.contains(level)) {
            dispatchDelayedDataLoader(resultPathWithDataLoader);
        }


    }

    private void dispatchDelayedDataLoader(ResultPathWithDataLoader resultPathWithDataLoader) {
        callStack.lock.runLocked(() -> {
            callStack.batchWindowOfDelayedDataLoaderToDispatch.add(resultPathWithDataLoader.resultPath);
            if (!callStack.batchWindowOpen) {
                callStack.batchWindowOpen = true;
                AtomicReference<Set<String>> dfesToDispatch = new AtomicReference<>();
                Runnable runnable = () -> {
                    callStack.lock.runLocked(() -> {
                        dfesToDispatch.set(new LinkedHashSet<>(callStack.batchWindowOfDelayedDataLoaderToDispatch));
                        callStack.batchWindowOfDelayedDataLoaderToDispatch.clear();
                        callStack.batchWindowOpen = false;
                    });
                    dispatchDLCFImpl(dfesToDispatch.get(), false);
                };
                try {
                    delayedDataLoaderDispatchExecutor.get().schedule(runnable, this.batchWindowNs, TimeUnit.NANOSECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
    }

    private static class ResultPathWithDataLoader {
        final String resultPath;
        final int level;
        final DataLoader dataLoader;

        public ResultPathWithDataLoader(String resultPath, int level, DataLoader dataLoader) {
            this.resultPath = resultPath;
            this.level = level;
            this.dataLoader = dataLoader;
        }
    }

}

