package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.Profiler;
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final long batchWindowNs;
    private final boolean enableDataLoaderChaining;

    private final InterThreadMemoizedSupplier<ScheduledExecutorService> delayedDataLoaderDispatchExecutor;

    static final InterThreadMemoizedSupplier<ScheduledExecutorService> defaultDelayedDLCFBatchWindowScheduler
            = new InterThreadMemoizedSupplier<>(() -> Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors()));

    static final long DEFAULT_BATCH_WINDOW_NANO_SECONDS_DEFAULT = 500_000L;
    private final Profiler profiler;


    private static class CallStack {

        private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();

        /**
         * A level is ready when all fields in this level are fetched
         * The expected field fetch count is accurate when all execute object calls happened
         * The expected execute object count is accurate when all sub selections fetched
         * are done in the previous level
         */

        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();

        // an object call means a sub selection of a field of type object/interface/union
        // the number of fields for sub selections increases the expected fetch count for this level
        private final LevelMap expectedExecuteObjectCallsPerLevel = new LevelMap();
        private final LevelMap happenedExecuteObjectCallsPerLevel = new LevelMap();

        // this means one sub selection has been fully fetched
        // and the expected execute objects calls for the next level have been calculated
        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = ConcurrentHashMap.newKeySet();

        // all levels that are ready to be dispatched
        private int highestReadyLevel;

        private final List<ResultPathWithDataLoader> allResultPathWithDataLoader = Collections.synchronizedList(new ArrayList<>());
        private final Map<Integer, Set<ResultPathWithDataLoader>> levelToResultPathWithDataLoader = new ConcurrentHashMap<>();

        private final Set<Integer> dispatchingStartedPerLevel = ConcurrentHashMap.newKeySet();
        private final Set<Integer> dispatchingFinishedPerLevel = ConcurrentHashMap.newKeySet();
        // Set of ResultPath
        private final Set<String> batchWindowOfDelayedDataLoaderToDispatch = ConcurrentHashMap.newKeySet();

        private boolean batchWindowOpen;


        public CallStack() {
            // in the first level there is only one sub selection,
            // so we only expect one execute object call (which is actually an executionStrategy call)
            expectedExecuteObjectCallsPerLevel.set(1, 1);
        }

        public void addResultPathWithDataLoader(int level, ResultPathWithDataLoader resultPathWithDataLoader) {
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

        boolean allSubSelectionsFetchingHappened(int level) {
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


        public void setDispatchedLevel(int level) {
            if (!dispatchedLevels.add(level)) {
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
            }
        }
    }

    public PerLevelDataLoaderDispatchStrategy(ExecutionContext executionContext) {
        this.callStack = new CallStack();
        this.executionContext = executionContext;

        GraphQLContext graphQLContext = executionContext.getGraphQLContext();
        this.batchWindowNs = graphQLContext.getOrDefault(DataLoaderDispatchingContextKeys.DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS, DEFAULT_BATCH_WINDOW_NANO_SECONDS_DEFAULT);

        this.delayedDataLoaderDispatchExecutor = new InterThreadMemoizedSupplier<>(() -> {
            DelayedDataLoaderDispatcherExecutorFactory delayedDataLoaderDispatcherExecutorFactory = graphQLContext.get(DataLoaderDispatchingContextKeys.DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY);
            if (delayedDataLoaderDispatcherExecutorFactory != null) {
                return delayedDataLoaderDispatcherExecutorFactory.createExecutor(executionContext.getExecutionId(), graphQLContext);
            }
            return defaultDelayedDLCFBatchWindowScheduler.get();
        });

        this.enableDataLoaderChaining = graphQLContext.getBoolean(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING, false);
        this.profiler = executionContext.getProfiler();
    }

    @Override
    public void executeDeferredOnFieldValueInfo(FieldValueInfo fieldValueInfo, ExecutionStrategyParameters executionStrategyParameters) {
        throw new UnsupportedOperationException("Data Loaders cannot be used to resolve deferred fields");
    }

    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        Assert.assertTrue(parameters.getExecutionStepInfo().getPath().isRootPath());
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(1, parameters);
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
            callStack.dispatchingFinishedPerLevel.clear();
            callStack.dispatchingStartedPerLevel.clear();
            callStack.allResultPathWithDataLoader.clear();
            callStack.batchWindowOfDelayedDataLoaderToDispatch.clear();
            callStack.batchWindowOpen = false;
            callStack.levelToResultPathWithDataLoader.clear();
            callStack.highestReadyLevel = 0;
        });
    }

    private void onFieldValuesInfoDispatchIfNeeded(List<FieldValueInfo> fieldValueInfoList, int curLevel) {
        Integer dispatchLevel = callStack.lock.callLocked(() ->
                handleOnFieldValuesInfo(fieldValueInfoList, curLevel)
        );
        // the handle on field values check for the next level if it is ready
        if (dispatchLevel != null) {
            dispatch(dispatchLevel);
        }
    }

    //
// thread safety: called with callStack.lock
//
    private Integer handleOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfos, int curLevel) {
        callStack.increaseHappenedOnFieldValueCalls(curLevel);
        int expectedOnObjectCalls = getObjectCountForList(fieldValueInfos);
        // on the next level we expect the following on object calls because we found non null objects
        callStack.increaseExpectedExecuteObjectCalls(curLevel + 1, expectedOnObjectCalls);
        // maybe the object calls happened already (because the DataFetcher return directly values synchronously)
        // therefore we check the next levels if they are ready
        // this means we could skip some level because the higher level is also already ready,
        // which means there is nothing to dispatch on these levels: if x and x+1 is ready, it means there are no
        // data loaders used on x
        //
        // if data loader chaining is disabled (the old algo) the level we dispatch is not really relevant as
        // we dispatch the whole registry anyway

        return getHighestReadyLevel(curLevel + 1);
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
        boolean ready = checkLevelBeingReady(level);
        if (ready) {
            callStack.setDispatchedLevel(level);
            return true;
        }
        return false;
    }

    //
// thread safety: called with callStack.lock
//
    private Integer getHighestReadyLevel(int startFrom) {
        int curLevel = callStack.highestReadyLevel;
        while (true) {
            if (!checkLevelImpl(curLevel + 1)) {
                callStack.highestReadyLevel = curLevel;
                return curLevel >= startFrom ? curLevel : null;
            }
            curLevel++;
        }
    }

    private boolean checkLevelBeingReady(int level) {
        Assert.assertTrue(level > 0);
        if (level <= callStack.highestReadyLevel) {
            return true;
        }

        for (int i = callStack.highestReadyLevel + 1; i <= level; i++) {
            if (!checkLevelImpl(i)) {
                return false;
            }
        }
        callStack.highestReadyLevel = level;
        return true;
    }

    private boolean checkLevelImpl(int level) {
        // a level with zero expectations can't be ready
        if (callStack.expectedFetchCountPerLevel.get(level) == 0) {
            return false;
        }
        // level 1 is special: there is no previous sub selections
        // and the expected execution object calls is always 1
        if (level > 1 && !callStack.allSubSelectionsFetchingHappened(level - 1)) {
            return false;
        }
        if (!callStack.allExecuteObjectCallsHappened(level)) {
            return false;
        }
        if (!callStack.allFetchesHappened(level)) {
            return false;
        }
        return true;
    }

    void dispatch(int level) {
        if (!enableDataLoaderChaining) {
            profiler.oldStrategyDispatchingAll(level);
            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            dataLoaderRegistry.dispatchAll();
            return;
        }
        Set<ResultPathWithDataLoader> resultPathWithDataLoaders = callStack.levelToResultPathWithDataLoader.get(level);
        if (resultPathWithDataLoaders != null) {
            profiler.chainedStrategyDispatching(level);
            Set<String> resultPathToDispatch = callStack.lock.callLocked(() -> {
                callStack.dispatchingStartedPerLevel.add(level);
                return resultPathWithDataLoaders
                        .stream()
                        .map(resultPathWithDataLoader -> resultPathWithDataLoader.resultPath)
                        .collect(Collectors.toSet());
            });
            dispatchDLCFImpl(resultPathToDispatch, level);
        } else {
            callStack.lock.runLocked(() -> {
                callStack.dispatchingStartedPerLevel.add(level);
                callStack.dispatchingFinishedPerLevel.add(level);
            });
        }
    }


    public void dispatchDLCFImpl(Set<String> resultPathsToDispatch, Integer level) {

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
            if (level != null) {
                callStack.dispatchingFinishedPerLevel.add(level);
            }
            return;
        }
        List<CompletableFuture> allDispatchedCFs = new ArrayList<>();
        for (ResultPathWithDataLoader resultPathWithDataLoader : relevantResultPathWithDataLoader) {
            allDispatchedCFs.add(resultPathWithDataLoader.dataLoader.dispatch());
        }
        CompletableFuture.allOf(allDispatchedCFs.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) -> {
                    dispatchDLCFImpl(resultPathsToDispatch, level);
                        }
                );

    }


    public void newDataLoaderLoadCall(String resultPath, int level, DataLoader dataLoader, String dataLoaderName, Object key) {
        if (!enableDataLoaderChaining) {
            return;
        }
        ResultPathWithDataLoader resultPathWithDataLoader = new ResultPathWithDataLoader(resultPath, level, dataLoader, dataLoaderName, key);
        boolean levelFinished = callStack.lock.callLocked(() -> {
            boolean finished = callStack.dispatchingFinishedPerLevel.contains(level);
            callStack.allResultPathWithDataLoader.add(resultPathWithDataLoader);
            // only add to the list of DataLoader for this level if we are not already dispatching
            if (!callStack.dispatchingStartedPerLevel.contains(level)) {
                callStack.addResultPathWithDataLoader(level, resultPathWithDataLoader);
            }
            return finished;
        });
        if (levelFinished) {
            newDelayedDataLoader(resultPathWithDataLoader);
        }


    }

    class DispatchDelayedDataloader implements Runnable {

        @Override
        public void run() {
            AtomicReference<Set<String>> resultPathToDispatch = new AtomicReference<>();
            callStack.lock.runLocked(() -> {
                resultPathToDispatch.set(new LinkedHashSet<>(callStack.batchWindowOfDelayedDataLoaderToDispatch));
                callStack.batchWindowOfDelayedDataLoaderToDispatch.clear();
                callStack.batchWindowOpen = false;
            });
            dispatchDLCFImpl(resultPathToDispatch.get(), null);
        }
    }

    private void newDelayedDataLoader(ResultPathWithDataLoader resultPathWithDataLoader) {
        callStack.lock.runLocked(() -> {
            callStack.batchWindowOfDelayedDataLoaderToDispatch.add(resultPathWithDataLoader.resultPath);
            if (!callStack.batchWindowOpen) {
                callStack.batchWindowOpen = true;
                delayedDataLoaderDispatchExecutor.get().schedule(new DispatchDelayedDataloader(), this.batchWindowNs, TimeUnit.NANOSECONDS);
            }

        });
    }

    private static class ResultPathWithDataLoader {
        final String resultPath;
        final int level;
        final DataLoader dataLoader;
        final String name;
        final Object key;

        public ResultPathWithDataLoader(String resultPath, int level, DataLoader dataLoader, String name, Object key) {
            this.resultPath = resultPath;
            this.level = level;
            this.dataLoader = dataLoader;
            this.name = name;
            this.key = key;
        }

        @Override
        public String toString() {
            return "ResultPathWithDataLoader{" +
                    "resultPath='" + resultPath + '\'' +
                    ", level=" + level +
                    ", key=" + key +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

}

