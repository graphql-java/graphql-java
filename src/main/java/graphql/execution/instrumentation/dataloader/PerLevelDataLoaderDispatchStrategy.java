package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.Profiler;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
import graphql.execution.incremental.AlternativeCallContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.util.InterThreadMemoizedSupplier;
import graphql.util.LockKit;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
@NullMarked
public class PerLevelDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final CallStack initialCallStack;
    private final ExecutionContext executionContext;
    private final long batchWindowNs;
    private final boolean enableDataLoaderChaining;

    private final InterThreadMemoizedSupplier<ScheduledExecutorService> delayedDataLoaderDispatchExecutor;

    static final InterThreadMemoizedSupplier<ScheduledExecutorService> defaultDelayedDLCFBatchWindowScheduler
            = new InterThreadMemoizedSupplier<>(() -> Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors()));

    static final long DEFAULT_BATCH_WINDOW_NANO_SECONDS_DEFAULT = 500_000L;
    private final Profiler profiler;

    private final Map<AlternativeCallContext, CallStack> deferredCallStackMap = new ConcurrentHashMap<>();


    private static class CallStack {

        private final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();

        /**
         * A general overview of teh tracked data:
         * There are three aspects tracked per level:
         * - number of execute object calls (executeObject)
         * - number of fetches
         * - number of sub selections finished fetching
         * <p/>
         * The level for an execute object call is the level of the field in the query: for
         * { a {b {c}}} the level of a is 1, b is 2 and c is not an object
         * <p/>
         * For fetches the level is the level of the field fetched
         * <p/>
         * For sub selections finished it is the level of the fields inside the sub selection:
         * {a1 { b c} a2 } the level of {a1 a2} is 1, the level of {b c} is 2
         * <p/>
         * <p/>
         * A finished subselection means we can predict the number of execute object calls in the same level as the subselection:
         * { a {x} b {y} }
         * If a is a list of 3 objects and b is a list of 2 objects we expect 3 + 2 = 5 execute object calls on the level 1 to be happening
         * <p/>
         * An executed object call again means we can predict the number of fetches in the next level:
         * Execute Object a with { a {f1 f2 f3} } means we expect 3 fetches on level 2.
         * <p/>
         * This means we know a level is ready to be dispatched if:
         * - all subselections done in the parent level
         * - all execute objects calls in the parent level are done
         * - all expected fetched happened in the current level
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


        private final List<FieldValueInfo> deferredFragmentRootFieldsFetched = new ArrayList<>();

        public CallStack() {
            // in the first level there is only one sub selection,
            // so we only expect one execute object call (which is actually an executionStrategy call)
            expectedExecuteObjectCallsPerLevel.set(0, 1);
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

        boolean allSubSelectionsFetchingHappened(int subSelectionLevel) {
            return happenedOnFieldValueCallsPerLevel.get(subSelectionLevel) == expectedExecuteObjectCallsPerLevel.get(subSelectionLevel - 1);
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
        this.initialCallStack = new CallStack();
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
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {
        Assert.assertTrue(parameters.getExecutionStepInfo().getPath().isRootPath());
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(0, fieldCount, initialCallStack);
    }

    @Override
    public void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        resetCallStack(callStack);
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(0, 1, callStack);
    }

    @Override
    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        // the root fields are the root sub selection on level 1
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, 1, callStack);
    }

    @Override
    public void executionStrategyOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(1)
        );
    }

    private CallStack getCallStack(ExecutionStrategyParameters parameters) {
        return getCallStack(parameters.getDeferredCallContext());
    }

    private CallStack getCallStack(@Nullable AlternativeCallContext alternativeCallContext) {
        if (alternativeCallContext == null) {
            return this.initialCallStack;
        } else {
            return deferredCallStackMap.computeIfAbsent(alternativeCallContext, k -> {
                CallStack callStack = new CallStack();
                int startLevel = alternativeCallContext.getStartLevel();
                int fields = alternativeCallContext.getFields();
                callStack.lock.runLocked(() -> {
                    // we make sure that startLevel-1 is considered done
                    callStack.expectedExecuteObjectCallsPerLevel.set(0, 0); // set to 1 in the constructor of CallStack
                    callStack.expectedExecuteObjectCallsPerLevel.set(startLevel - 1, 1);
                    callStack.happenedExecuteObjectCallsPerLevel.set(startLevel - 1, 1);
                    callStack.highestReadyLevel = startLevel - 1;
                    callStack.increaseExpectedFetchCount(startLevel, fields);
                });
                return callStack;
            });
        }
    }

    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {
        CallStack callStack = getCallStack(parameters);
        int curLevel = parameters.getPath().getLevel();
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(curLevel, fieldCount, callStack);
    }

    @Override
    public void executeObjectOnFieldValuesInfo
            (List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        // the level of the sub selection that is fully fetched is one level more than parameters level
        int curLevel = parameters.getPath().getLevel() + 1;
        CallStack callStack = getCallStack(parameters);
        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel, callStack);
    }


    @Override
    public void newSubscriptionExecution(FieldValueInfo fieldValueInfo, AlternativeCallContext alternativeCallContext) {
        CallStack callStack = getCallStack(alternativeCallContext);
        callStack.increaseFetchCount(1);
        callStack.deferredFragmentRootFieldsFetched.add(fieldValueInfo);
        onFieldValuesInfoDispatchIfNeeded(callStack.deferredFragmentRootFieldsFetched, 1, callStack);
    }

    @Override
    public void deferredOnFieldValue(String resultKey, FieldValueInfo fieldValueInfo, Throwable
            throwable, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        boolean ready = callStack.lock.callLocked(() -> {
            callStack.deferredFragmentRootFieldsFetched.add(fieldValueInfo);
            Assert.assertNotNull(parameters.getDeferredCallContext());
            return callStack.deferredFragmentRootFieldsFetched.size() == parameters.getDeferredCallContext().getFields();
        });
        if (ready) {
            int curLevel = parameters.getPath().getLevel();
            onFieldValuesInfoDispatchIfNeeded(callStack.deferredFragmentRootFieldsFetched, curLevel, callStack);
        }
    }

    @Override
    public void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        // the level of the sub selection that is errored is one level more than parameters level
        int curLevel = parameters.getPath().getLevel() + 1;
        callStack.lock.runLocked(() ->
                callStack.increaseHappenedOnFieldValueCalls(curLevel)
        );
    }


    private void increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(int curLevel,
                                                                            int fieldCount,
                                                                            CallStack callStack) {
        callStack.lock.runLocked(() -> {
            callStack.increaseHappenedExecuteObjectCalls(curLevel);
            callStack.increaseExpectedFetchCount(curLevel + 1, fieldCount);
        });
    }

    private void resetCallStack(CallStack callStack) {
        callStack.lock.runLocked(() -> {
            callStack.clearDispatchLevels();
            callStack.clearExpectedObjectCalls();
            callStack.clearExpectedFetchCount();
            callStack.clearFetchCount();
            callStack.clearHappenedExecuteObjectCalls();
            callStack.clearHappenedOnFieldValueCalls();
            callStack.expectedExecuteObjectCallsPerLevel.set(0, 1);
            callStack.dispatchingFinishedPerLevel.clear();
            callStack.dispatchingStartedPerLevel.clear();
            callStack.allResultPathWithDataLoader.clear();
            callStack.batchWindowOfDelayedDataLoaderToDispatch.clear();
            callStack.batchWindowOpen = false;
            callStack.levelToResultPathWithDataLoader.clear();
            callStack.highestReadyLevel = 0;
        });
    }

    private void onFieldValuesInfoDispatchIfNeeded(List<FieldValueInfo> fieldValueInfoList,
                                                   int subSelectionLevel,
                                                   CallStack callStack) {
        Integer dispatchLevel = callStack.lock.callLocked(() ->
                handleSubSelectionFetched(fieldValueInfoList, subSelectionLevel, callStack)
        );
        // the handle on field values check for the next level if it is ready
        if (dispatchLevel != null) {
            dispatch(dispatchLevel, callStack);
        }
    }

    //
// thread safety: called with callStack.lock
//
    private @Nullable Integer handleSubSelectionFetched(List<FieldValueInfo> fieldValueInfos, int subSelectionLevel, CallStack
            callStack) {
        callStack.increaseHappenedOnFieldValueCalls(subSelectionLevel);
        int expectedOnObjectCalls = getObjectCountForList(fieldValueInfos);
        // we expect on the level of the current sub selection #expectedOnObjectCalls execute object calls
        callStack.increaseExpectedExecuteObjectCalls(subSelectionLevel, expectedOnObjectCalls);
        // maybe the object calls happened already (because the DataFetcher return directly values synchronously)
        // therefore we check the next levels if they are ready
        // this means we could skip some level because the higher level is also already ready,
        // which means there is nothing to dispatch on these levels: if x and x+1 is ready, it means there are no
        // data loaders used on x
        //
        // if data loader chaining is disabled (the old algo) the level we dispatch is not really relevant as
        // we dispatch the whole registry anyway

        return getHighestReadyLevel(subSelectionLevel + 1, callStack);
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
        CallStack callStack = getCallStack(executionStrategyParameters);
        int level = executionStrategyParameters.getPath().getLevel();
        boolean dispatchNeeded = callStack.lock.callLocked(() -> {
            callStack.increaseFetchCount(level);
            return dispatchIfNeeded(level, callStack);
        });
        if (dispatchNeeded) {
            dispatch(level, callStack);
        }

    }


    //
// thread safety : called with callStack.lock
//
    private boolean dispatchIfNeeded(int level, CallStack callStack) {
        boolean ready = checkLevelBeingReady(level, callStack);
        if (ready) {
            callStack.setDispatchedLevel(level);
            return true;
        }
        return false;
    }

    //
// thread safety: called with callStack.lock
//
    private @Nullable Integer getHighestReadyLevel(int startFrom, CallStack callStack) {
        int curLevel = callStack.highestReadyLevel;
        while (true) {
            if (!checkLevelImpl(curLevel + 1, callStack)) {
                callStack.highestReadyLevel = curLevel;
                return curLevel >= startFrom ? curLevel : null;
            }
            curLevel++;
        }
    }

    private boolean checkLevelBeingReady(int level, CallStack callStack) {
        Assert.assertTrue(level > 0);
        if (level <= callStack.highestReadyLevel) {
            return true;
        }

        for (int i = callStack.highestReadyLevel + 1; i <= level; i++) {
            if (!checkLevelImpl(i, callStack)) {
                return false;
            }
        }
        callStack.highestReadyLevel = level;
        return true;
    }

    private boolean checkLevelImpl(int level, CallStack callStack) {
        // a level with zero expectations can't be ready
        if (callStack.expectedFetchCountPerLevel.get(level) == 0) {
            return false;
        }

        // first we make sure that the expected fetch count is correct
        // by verifying that the parent level all execute object + sub selection were fetched
        if (!callStack.allExecuteObjectCallsHappened(level - 1)) {
            return false;
        }
        if (level > 1 && !callStack.allSubSelectionsFetchingHappened(level - 1)) {
            return false;
        }
        // the main check: all fetches must have happened
        if (!callStack.allFetchesHappened(level)) {
            return false;
        }
        return true;
    }

    void dispatch(int level, CallStack callStack) {
        if (!enableDataLoaderChaining) {
            profiler.oldStrategyDispatchingAll(level);
            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            dispatchAll(dataLoaderRegistry, level);
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
            dispatchDLCFImpl(resultPathToDispatch, level, callStack);
        } else {
            callStack.lock.runLocked(() -> {
                callStack.dispatchingStartedPerLevel.add(level);
                callStack.dispatchingFinishedPerLevel.add(level);
            });
        }
    }

    private void dispatchAll(DataLoaderRegistry dataLoaderRegistry, int level) {
        for (DataLoader<?, ?> dataLoader : dataLoaderRegistry.getDataLoaders()) {
            dataLoader.dispatch().whenComplete((objects, throwable) -> {
                if (objects != null && objects.size() > 0) {
                    Assert.assertNotNull(dataLoader.getName());
                    profiler.batchLoadedOldStrategy(dataLoader.getName(), level, objects.size());
                }
            });
        }
    }

    private void dispatchDLCFImpl(Set<String> resultPathsToDispatch, @Nullable Integer level, CallStack callStack) {

        // filter out all DataLoaderCFS that are matching the fields we want to dispatch
        List<ResultPathWithDataLoader> relevantResultPathWithDataLoader = new ArrayList<>();
        // we need to copy the list because the callStack.allResultPathWithDataLoader can be modified concurrently
        // while iterating over it
        ArrayList<ResultPathWithDataLoader> resultPathWithDataLoaders = new ArrayList<>(callStack.allResultPathWithDataLoader);
        for (ResultPathWithDataLoader resultPathWithDataLoader : resultPathWithDataLoaders) {
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
            CompletableFuture<List> dispatch = resultPathWithDataLoader.dataLoader.dispatch();
            allDispatchedCFs.add(dispatch);
            dispatch.whenComplete((objects, throwable) -> {
                if (objects != null && objects.size() > 0) {
                    profiler.batchLoadedNewStrategy(resultPathWithDataLoader.name, level, objects.size());
                }
            });
        }
        CompletableFuture.allOf(allDispatchedCFs.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) -> {
                    dispatchDLCFImpl(resultPathsToDispatch, level, callStack);
                        }
                );

    }


    public void newDataLoaderLoadCall(String resultPath, int level, DataLoader dataLoader, String
            dataLoaderName, Object key, @Nullable AlternativeCallContext alternativeCallContext) {
        if (!enableDataLoaderChaining) {
            return;
        }
        ResultPathWithDataLoader resultPathWithDataLoader = new ResultPathWithDataLoader(resultPath, level, dataLoader, dataLoaderName, key);
        CallStack callStack = getCallStack(alternativeCallContext);
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
            newDelayedDataLoader(resultPathWithDataLoader, callStack);
        }


    }

    class DispatchDelayedDataloader implements Runnable {

        private final CallStack callStack;

        public DispatchDelayedDataloader(CallStack callStack) {
            this.callStack = callStack;
        }

        @Override
        public void run() {
            AtomicReference<Set<String>> resultPathToDispatch = new AtomicReference<>();
            callStack.lock.runLocked(() -> {
                resultPathToDispatch.set(new LinkedHashSet<>(callStack.batchWindowOfDelayedDataLoaderToDispatch));
                callStack.batchWindowOfDelayedDataLoaderToDispatch.clear();
                callStack.batchWindowOpen = false;
            });
            dispatchDLCFImpl(Assert.assertNotNull(resultPathToDispatch.get()), null, callStack);
        }
    }

    private void newDelayedDataLoader(ResultPathWithDataLoader resultPathWithDataLoader, CallStack callStack) {
        callStack.lock.runLocked(() -> {
            callStack.batchWindowOfDelayedDataLoaderToDispatch.add(resultPathWithDataLoader.resultPath);
            if (!callStack.batchWindowOpen) {
                callStack.batchWindowOpen = true;
                delayedDataLoaderDispatchExecutor.get().schedule(new DispatchDelayedDataloader(callStack), this.batchWindowNs, TimeUnit.NANOSECONDS);
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

