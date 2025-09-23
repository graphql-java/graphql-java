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
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Internal
@NullMarked
public class PerLevelDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final CallStack initialCallStack;
    private final ExecutionContext executionContext;
    private final boolean enableDataLoaderChaining;


    private final Profiler profiler;

    private final Map<AlternativeCallContext, CallStack> alternativeCallContextMap = new ConcurrentHashMap<>();

    private static class ChainedDLStack {

        private final Map<Integer, AtomicReference<@Nullable StateForLevel>> stateMapPerLevel = new ConcurrentHashMap<>();

        // a state for level points to a previous one
        // all the invocations that are linked together are the relevant invocations for the next dispatch
        private static class StateForLevel {
            final @Nullable DataLoaderInvocation dataLoaderInvocation;
            final boolean dispatchingStarted;
            final boolean dispatchingFinished;
            final boolean currentlyDelayedDispatching;
            final @Nullable StateForLevel prev;

            public StateForLevel(@Nullable DataLoaderInvocation dataLoaderInvocation,
                                 boolean dispatchingStarted,
                                 boolean dispatchingFinished,
                                 boolean currentlyDelayedDispatching,
                                 @Nullable StateForLevel prev) {
                this.dataLoaderInvocation = dataLoaderInvocation;
                this.dispatchingStarted = dispatchingStarted;
                this.dispatchingFinished = dispatchingFinished;
                this.currentlyDelayedDispatching = currentlyDelayedDispatching;
                this.prev = prev;
            }
        }


        public @Nullable StateForLevel aboutToStartDispatching(int level, boolean normalDispatchOrDelayed, boolean chained) {
            AtomicReference<@Nullable StateForLevel> currentStateRef = stateMapPerLevel.computeIfAbsent(level, __ -> new AtomicReference<>());
            while (true) {
                StateForLevel currentState = currentStateRef.get();


                boolean dispatchingStarted = currentState != null && currentState.dispatchingStarted;
                boolean dispatchingFinished = currentState != null && currentState.dispatchingFinished;
                boolean currentlyDelayedDispatching = currentState != null && currentState.currentlyDelayedDispatching;

                if (!chained) {
                    if (normalDispatchOrDelayed) {
                        dispatchingStarted = true;
                    } else {
                        currentlyDelayedDispatching = true;
                    }
                }

                if (currentState == null || currentState.dataLoaderInvocation == null) {
                    if (normalDispatchOrDelayed) {
                        dispatchingFinished = true;
                    } else {
                        currentlyDelayedDispatching = false;
                    }
                }

                StateForLevel newState = new StateForLevel(null, dispatchingStarted, dispatchingFinished, currentlyDelayedDispatching, null);

                if (currentStateRef.compareAndSet(currentState, newState)) {
                    return currentState;
                }
            }
        }


        public boolean newDataLoaderInvocation(DataLoaderInvocation dataLoaderInvocation) {
            int level = dataLoaderInvocation.level;
            AtomicReference<@Nullable StateForLevel> currentStateRef = stateMapPerLevel.computeIfAbsent(level, __ -> new AtomicReference<>());
            while (true) {
                StateForLevel currentState = currentStateRef.get();


                boolean dispatchingStarted = currentState != null && currentState.dispatchingStarted;
                boolean dispatchingFinished = currentState != null && currentState.dispatchingFinished;
                boolean currentlyDelayedDispatching = currentState != null && currentState.currentlyDelayedDispatching;

                // we need to start a new delayed dispatching if
                // the normal dispatching is finished and there is no currently delayed dispatching for this level
                boolean newDelayedInvocation = dispatchingFinished && !currentlyDelayedDispatching;
                if (newDelayedInvocation) {
                    currentlyDelayedDispatching = true;
                }

                StateForLevel newState = new StateForLevel(dataLoaderInvocation, dispatchingStarted, dispatchingFinished, currentlyDelayedDispatching, currentState);

                if (currentStateRef.compareAndSet(currentState, newState)) {
                    return newDelayedInvocation;
                }
            }
        }

        public void clear() {
            stateMapPerLevel.clear();
        }

    }

    private static class CallStack {


        /**
         * A general overview of teh tracked data:
         * There are three aspects tracked per level:
         * - number of expected and happened execute object calls (executeObject)
         * - number of expected and happened fetches
         * - number of happened sub selections finished fetching
         * <p/>
         * The level for an execute object call is the level of sub selection of the object: for
         * { a {b {c}}} the level of "execute object a" is 2
         * <p/>
         * For fetches the level is the level of the field fetched
         * <p/>
         * For sub selections finished it is the level of the fields inside the sub selection:
         * {a1 { b c} a2 } the level of {a1 a2} is 1, the level of {b c} is 2
         * <p/>
         * The main aspect for when a level is ready is when all expected fetch call happened, meaning
         * we can dispatch this level as all data loaders in this level have been called
         * (if the number of expected fetches is correct).
         * <p/>
         * The number of expected fetches is increased with every executeObject (based on the number of subselection
         * fields for the execute object).
         * Execute Object a (on level 2) with { a {f1 f2 f3} } means we expect 3 fetches on level 2.
         * <p/>
         * A finished subselection means we can predict the number of execute object calls in the next level as the subselection:
         * { a {x} b {y} }
         * If a is a list of 3 objects and b is a list of 2 objects we expect 3 + 2 = 5 execute object calls on the level 2 to be happening
         * <p/>
         * The finished sub selection is the only "cross level" event: a finished sub selections impacts the expected execute
         * object calls on the next level.
         * <p/>
         * <p/>
         * This means we know a level is ready to be dispatched if:
         * - all expected fetched happened in the current level
         * - all expected execute objects calls happened in the current level (because they inform the expected fetches)
         * - all expected sub selections happened in the parent level (because they inform the expected execute object in the current level).
         * The expected sub selections are equal to the expected object calls (in the parent level)
         * - All expected sub selections happened in the parent parent level (again: meaning #happenedSubSelections == #expectedExecuteObjectCalls)
         * - And so until the first level
         */

        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap fetchCountPerLevel = new LevelMap();

        // an object call means a sub selection of a field of type object/interface/union
        // the number of fields for sub selections increases the expected fetch count for this level
//        private final LevelMap expectedExecuteObjectCallsPerLevel = new LevelMap();
//        private final LevelMap happenedExecuteObjectCallsPerLevel = new LevelMap();

        private final LevelMap happenedCompleteFieldPerLevel = new LevelMap();

        // this means one sub selection has been fully fetched
        // and the expected execute objects calls for the next level have been calculated
//        private final LevelMap happenedOnFieldValueCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = ConcurrentHashMap.newKeySet();


        public ChainedDLStack chainedDLStack = new ChainedDLStack();

        private final List<FieldValueInfo> deferredFragmentRootFieldsFetched = new ArrayList<>();

        public CallStack() {
            // in the first level there is only one sub selection,
            // so we only expect one execute object call (which is actually an executionStrategy call)
//            expectedExecuteObjectCallsPerLevel.set(1, 1);
        }


        void increaseExpectedFetchCount(int level, int count) {
            expectedFetchCountPerLevel.increment(level, count);
        }

        void clearExpectedFetchCount() {
            expectedFetchCountPerLevel.clear();
        }

        void increaseHappenedFetchCount(int level) {
            fetchCountPerLevel.increment(level, 1);
        }


        void clearFetchCount() {
            fetchCountPerLevel.clear();
        }

        void increaseExpectedExecuteObjectCalls(int level, int count) {
//            expectedExecuteObjectCallsPerLevel.increment(level, count);
        }

//        void clearExpectedObjectCalls() {
//            expectedExecuteObjectCallsPerLevel.clear();
//        }
//
//        void increaseHappenedExecuteObjectCalls(int level) {
//            happenedExecuteObjectCallsPerLevel.increment(level, 1);
//        }
//
//        void clearHappenedExecuteObjectCalls() {
//            happenedExecuteObjectCallsPerLevel.clear();
//        }

//        void increaseHappenedOnFieldValueCalls(int level) {
//            happenedOnFieldValueCallsPerLevel.increment(level, 1);
//        }
//
//        void clearHappenedOnFieldValueCalls() {
//            happenedOnFieldValueCallsPerLevel.clear();
//        }

//        boolean allExecuteObjectCallsHappened(int level) {
//            return happenedExecuteObjectCallsPerLevel.get(level) == expectedExecuteObjectCallsPerLevel.get(level);
//        }

//        boolean allSubSelectionsFetchingHappened(int level) {
//            return happenedOnFieldValueCallsPerLevel.get(level) == expectedExecuteObjectCallsPerLevel.get(level);
//        }
//

        boolean allFieldsCompleted(int level) {
            return fetchCountPerLevel.get(level) == happenedCompleteFieldPerLevel.get(level);
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
//                   ", expectedExecuteObjectCallsPerLevel=" + expectedExecuteObjectCallsPerLevel +
//                   ", happenedExecuteObjectCallsPerLevel=" + happenedExecuteObjectCallsPerLevel +
//                   ", happenedOnFieldValueCallsPerLevel=" + happenedOnFieldValueCallsPerLevel +
                   ", dispatchedLevels" + dispatchedLevels +
                   '}';
        }


        public void setDispatchedLevel(int level) {
            if (!dispatchedLevels.add(level)) {
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
            }
        }

        public void clearHappenedCompleteFields() {
            this.happenedCompleteFieldPerLevel.clear();

        }
    }

    public PerLevelDataLoaderDispatchStrategy(ExecutionContext executionContext) {
        this.initialCallStack = new CallStack();
        this.executionContext = executionContext;

        GraphQLContext graphQLContext = executionContext.getGraphQLContext();

        this.enableDataLoaderChaining = graphQLContext.getBoolean(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING, false);
        this.profiler = executionContext.getProfiler();
    }


    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {
        Assert.assertTrue(parameters.getExecutionStepInfo().getPath().isRootPath());
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(1, fieldCount, initialCallStack);
    }

    @Override
    public void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        resetCallStack(callStack);
        // field count is always 1 for serial execution
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(1, 1, callStack);
    }

//    @Override
//    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
//        CallStack callStack = getCallStack(parameters);
//        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, 1, callStack);
//    }

//    @Override
//    public void executionStrategyOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
//        CallStack callStack = getCallStack(parameters);
//        synchronized (callStack) {
//            callStack.increaseHappenedOnFieldValueCalls(1);
//        }
//    }

    private CallStack getCallStack(ExecutionStrategyParameters parameters) {
        return getCallStack(parameters.getDeferredCallContext());
    }

    private CallStack getCallStack(@Nullable AlternativeCallContext alternativeCallContext) {
        if (alternativeCallContext == null) {
            return this.initialCallStack;
        } else {
            return alternativeCallContextMap.computeIfAbsent(alternativeCallContext, k -> {
                CallStack callStack = new CallStack();
                int startLevel = alternativeCallContext.getStartLevel();
                int fields = alternativeCallContext.getFields();
                System.out.println("startLevel for new callstack " + startLevel);
                // we make sure that startLevel is considered done
                for (int i = 1; i <= startLevel; i++) {
                    callStack.increaseExpectedFetchCount(startLevel, 1);
                    callStack.increaseHappenedFetchCount(1);
                    if (i < startLevel) {
                        callStack.happenedCompleteFieldPerLevel.set(startLevel, 1);
                    }
                }
                return callStack;
            });
        }
    }

    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {
        CallStack callStack = getCallStack(parameters);
        int curLevel = parameters.getPath().getLevel();
        increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(curLevel + 1, fieldCount, callStack);
    }

//    @Override
//    public void executeObjectOnFieldValuesInfo
//            (List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
//        // the level of the sub selection that is fully fetched is one level more than parameters level
//        int curLevel = parameters.getPath().getLevel() + 1;
//        CallStack callStack = getCallStack(parameters);
//        onFieldValuesInfoDispatchIfNeeded(fieldValueInfoList, curLevel, callStack);
//    }


    @Override
    public void newSubscriptionExecution(FieldValueInfo fieldValueInfo, AlternativeCallContext alternativeCallContext) {
        CallStack callStack = getCallStack(alternativeCallContext);
        callStack.increaseHappenedFetchCount(1);
        callStack.deferredFragmentRootFieldsFetched.add(fieldValueInfo);
//        onFieldValuesInfoDispatchIfNeeded(callStack.deferredFragmentRootFieldsFetched, 1, callStack);
    }

    @Override
    public void deferredOnFieldValue(String resultKey, FieldValueInfo fieldValueInfo, Throwable
            throwable, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        boolean ready;
        synchronized (callStack) {
            callStack.deferredFragmentRootFieldsFetched.add(fieldValueInfo);
            Assert.assertNotNull(parameters.getDeferredCallContext());
            ready = callStack.deferredFragmentRootFieldsFetched.size() == parameters.getDeferredCallContext().getFields();
        }
//        if (ready) {
//            int curLevel = parameters.getPath().getLevel();
//            onFieldValuesInfoDispatchIfNeeded(callStack.deferredFragmentRootFieldsFetched, curLevel, callStack);
//        }
    }

//    @Override
//    public void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
//        CallStack callStack = getCallStack(parameters);
//        // the level of the sub selection that is errored is one level more than parameters level
//        int curLevel = parameters.getPath().getLevel() + 1;
//        synchronized (callStack) {
//            callStack.increaseHappenedOnFieldValueCalls(curLevel);
//        }
//    }
//

    private void increaseHappenedExecuteObjectAndIncreaseExpectedFetchCount(int curLevel,
                                                                            int fieldCount,
                                                                            CallStack callStack) {
        synchronized (callStack) {
            callStack.increaseExpectedFetchCount(curLevel, fieldCount);
        }
    }

    private void resetCallStack(CallStack callStack) {
        synchronized (callStack) {
            callStack.clearDispatchLevels();
//            callStack.clearExpectedObjectCalls();
            callStack.clearHappenedCompleteFields();
            callStack.clearExpectedFetchCount();
            callStack.clearFetchCount();
//            callStack.clearHappenedExecuteObjectCalls();
//            callStack.clearHappenedOnFieldValueCalls();
//            callStack.expectedExecuteObjectCallsPerLevel.set(1, 1);
            callStack.chainedDLStack.clear();
        }
    }

//    private void onFieldValuesInfoDispatchIfNeeded(List<FieldValueInfo> fieldValueInfoList,
//                                                   int subSelectionLevel,
//                                                   CallStack callStack) {
//        Integer dispatchLevel;
//        synchronized (callStack) {
//            dispatchLevel = handleSubSelectionFetched(fieldValueInfoList, subSelectionLevel, callStack);
//        }
//        // the handle on field values check for the next level if it is ready
//        if (dispatchLevel != null) {
//            dispatch(dispatchLevel, callStack);
//        }
//    }

    @Override
    public void fieldCompleted(FieldValueInfo fieldValueInfo, ExecutionStrategyParameters parameters) {
        int level = parameters.getPath().getLevel();
//        System.out.println("field completed at level: " + level + " at: " + parameters.getPath());
        CallStack callStack = getCallStack(parameters);
        int currentLevel = parameters.getPath().getLevel() + 1;
        synchronized (callStack) {
            callStack.happenedCompleteFieldPerLevel.increment(level, 1);
        }
        while (true) {
            boolean levelReady;
            synchronized (callStack) {
                if (callStack.dispatchedLevels.contains(currentLevel)) {
                    break;
                }
                levelReady = dispatchIfNeeded(currentLevel, callStack);
            }
            if (levelReady) {
                dispatch(currentLevel, callStack);
            } else {
                break;
            }
            currentLevel++;
        }
    }

    //
// thread safety: called with callStack.lock
//
//    private @Nullable Integer handleSubSelectionFetched(List<FieldValueInfo> fieldValueInfos, int subSelectionLevel, CallStack
//            callStack) {
//        System.out.println("sub selection fetched at level :" + subSelectionLevel);
//        callStack.increaseHappenedOnFieldValueCalls(subSelectionLevel);
//        int expectedOnObjectCalls = getObjectCountForList(fieldValueInfos);
//        // we expect on the next level of the current sub selection #expectedOnObjectCalls execute object calls
//        callStack.increaseExpectedExecuteObjectCalls(subSelectionLevel + 1, expectedOnObjectCalls);
//
//        // maybe the object calls happened already (because the DataFetcher return directly values synchronously)
//        // therefore we check the next levels if they are ready
//        // if data loader chaining is disabled (the old algo) the level we dispatch is not really relevant as
//        // we dispatch the whole registry anyway
//
//        if (checkLevelImpl(subSelectionLevel + 1, callStack)) {
//            return subSelectionLevel + 1;
//        } else {
//            return null;
//        }
//    }

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
        boolean dispatchNeeded;
        synchronized (callStack) {
            System.out.println("field fetched at level " + level);
            callStack.increaseHappenedFetchCount(level);
            dispatchNeeded = dispatchIfNeeded(level, callStack);
        }
        if (dispatchNeeded) {
            dispatch(level, callStack);
        }

    }


    //
// thread safety : called with callStack.lock
//
    private boolean dispatchIfNeeded(int level, CallStack callStack) {
        boolean ready = checkLevelImpl(level, callStack);
        if (ready) {
            callStack.setDispatchedLevel(level);
            return true;
        }
        return false;
    }

    //
// thread safety: called with callStack.lock
//
//    private @Nullable Integer getHighestReadyLevel(int startFrom, CallStack callStack) {
//        while (true) {
//            if (!checkLevelImpl(curLevel + 1, callStack)) {
//                callStack.highestReadyLevel = curLevel;
//                return curLevel >= startFrom ? curLevel : null;
//            }
//            curLevel++;
//        }
//    }

//    private boolean checkLevelBeingReady(int level, CallStack callStack) {
//        Assert.assertTrue(level > 0);
//
//        for (int i = callStack.highestReadyLevel + 1; i <= level; i++) {
//            if (!checkLevelImpl(i, callStack)) {
//                return false;
//            }
//        }
//        callStack.highestReadyLevel = level;
//        return true;
//    }

    private boolean checkLevelImpl(int level, CallStack callStack) {
        System.out.println("checkLevelImpl " + level);
        // a level with zero expectations can't be ready
        if (callStack.expectedFetchCountPerLevel.get(level) == 0) {
            return false;
        }

        // all fetches happened
        if (!callStack.allFetchesHappened(level)) {
            return false;
        }
//        // the fetch count is actually correct because all execute object happened
//        if (!callStack.allFieldsCompleted(level-1)) {
//            return false;
//        }
        // the expected execute object call is correct because all sub selections got fetched on the parent
        // and their parent and their parent etc
        int levelTmp = level - 1;
        while (levelTmp >= 1) {
            if (!callStack.allFieldsCompleted(levelTmp)) {
                return false;
            }
            levelTmp--;
        }
        System.out.println("check ready " + level);
        return true;
    }

    void dispatch(int level, CallStack callStack) {
        if (!enableDataLoaderChaining) {
            profiler.oldStrategyDispatchingAll(level);
            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            dispatchAll(dataLoaderRegistry, level);
            return;
        }
//        System.out.println("dispatching " + level);
        dispatchDLCFImpl(level, callStack, true, false);
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

    private void dispatchDLCFImpl(Integer level, CallStack callStack, boolean normalOrDelayed, boolean chained) {

        ChainedDLStack.StateForLevel stateForLevel = callStack.chainedDLStack.aboutToStartDispatching(level, normalOrDelayed, chained);
        if (stateForLevel == null || stateForLevel.dataLoaderInvocation == null) {
            return;
        }

        List<CompletableFuture> allDispatchedCFs = new ArrayList<>();
        while (stateForLevel != null && stateForLevel.dataLoaderInvocation != null) {
            final DataLoaderInvocation invocation = stateForLevel.dataLoaderInvocation;
            CompletableFuture<List> dispatch = invocation.dataLoader.dispatch();
            allDispatchedCFs.add(dispatch);
            dispatch.whenComplete((objects, throwable) -> {
                if (objects != null && objects.size() > 0) {
                    profiler.batchLoadedNewStrategy(invocation.name, level, objects.size(), !normalOrDelayed, chained);
                }
            });
            stateForLevel = stateForLevel.prev;
        }
        CompletableFuture.allOf(allDispatchedCFs.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) -> {
                    dispatchDLCFImpl(level, callStack, normalOrDelayed, true);
                        }
                );

    }


    public void newDataLoaderInvocation(String resultPath,
                                        int level,
                                        DataLoader dataLoader,
                                        String dataLoaderName,
                                        Object key,
                                        @Nullable AlternativeCallContext alternativeCallContext) {
        if (!enableDataLoaderChaining) {
            return;
        }
        DataLoaderInvocation dataLoaderInvocation = new DataLoaderInvocation(resultPath, level, dataLoader, dataLoaderName, key);
        CallStack callStack = getCallStack(alternativeCallContext);
        boolean newDelayedInvocation = callStack.chainedDLStack.newDataLoaderInvocation(dataLoaderInvocation);
        if (newDelayedInvocation) {
            dispatchDLCFImpl(level, callStack, false, false);
        }
    }

    /**
     * A single data loader invocation.
     */
    private static class DataLoaderInvocation {
        final String resultPath;
        final int level;
        final DataLoader dataLoader;
        final String name;
        final Object key;

        public DataLoaderInvocation(String resultPath, int level, DataLoader dataLoader, String name, Object key) {
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

