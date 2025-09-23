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

        private static class StateForLevel {
            final boolean dispatched;
            final int expectedFetchCount;
            final int happenedFetchCount;
            final int happenedCompleteFieldCount;

            public StateForLevel() {
                this.dispatched = false;
                this.expectedFetchCount = 0;
                this.happenedFetchCount = 0;
                this.happenedCompleteFieldCount = 0;
            }

            public StateForLevel(boolean dispatched, int expectedFetchCount, int happenedFetchCount, int happenedCompleteFieldCount) {
                this.dispatched = dispatched;
                this.expectedFetchCount = expectedFetchCount;
                this.happenedFetchCount = happenedFetchCount;
                this.happenedCompleteFieldCount = happenedCompleteFieldCount;
            }

            public StateForLevel(StateForLevel other) {
                this.dispatched = other.dispatched;
                this.expectedFetchCount = other.expectedFetchCount;
                this.happenedFetchCount = other.happenedFetchCount;
                this.happenedCompleteFieldCount = other.happenedCompleteFieldCount;
            }

            public StateForLevel copy() {
                return new StateForLevel(this);
            }

            boolean allFieldsCompleted() {
                return expectedFetchCount == happenedCompleteFieldCount;
            }

            boolean allFetchesHappened() {
                return expectedFetchCount > 0 && expectedFetchCount == happenedFetchCount;
            }


        }

        private final Map<Integer, AtomicReference<StateForLevel>> stateForLevelMap = new ConcurrentHashMap<>();

        public void clear() {
            stateForLevelMap.clear();
        }

        public StateForLevel get(int level) {
            AtomicReference<StateForLevel> dataPerLevelAtomicReference = stateForLevelMap.computeIfAbsent(level, __ -> new AtomicReference<>(new StateForLevel()));
            return Assert.assertNotNull(dataPerLevelAtomicReference.get());
        }

        public boolean tryUpdateLevel(int level, StateForLevel oldData, StateForLevel newData) {
            AtomicReference<StateForLevel> dataPerLevelAtomicReference = Assert.assertNotNull(stateForLevelMap.get(level));
            return dataPerLevelAtomicReference.compareAndSet(oldData, newData);
        }

        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap happenedFetchCountPerLevel = new LevelMap();
        // happened complete field implies that the expected fetch field is correct
        // because completion triggers all needed executeObject, which determines the expected fetch field count
        private final LevelMap happenedCompleteFieldPerLevel = new LevelMap();

//        private final Set<Integer> dispatchedLevels = ConcurrentHashMap.newKeySet();

        public ChainedDLStack chainedDLStack = new ChainedDLStack();

        private final List<FieldValueInfo> deferredFragmentRootFieldsFetched = new ArrayList<>();

        public CallStack() {
        }


//        void increaseExpectedFetchCount(int level, int count) {
//            expectedFetchCountPerLevel.increment(level, count);
//        }
//
//        void clearExpectedFetchCount() {
//            expectedFetchCountPerLevel.clear();
//        }
//
//        void increaseHappenedFetchCount(int level) {
//            happenedFetchCountPerLevel.increment(level, 1);
//        }
//
//
//        void clearHappenedFetchCount() {
//            happenedFetchCountPerLevel.clear();
//        }
//
//
//        boolean allFieldsCompleted(int level) {
//            return happenedFetchCountPerLevel.get(level) == happenedCompleteFieldPerLevel.get(level);
//        }
//
//        boolean allFetchesHappened(int level) {
//            return happenedFetchCountPerLevel.get(level) == expectedFetchCountPerLevel.get(level);
//        }
//
//        void clearDispatchLevels() {
//            dispatchedLevels.clear();
//        }

        @Override
        public String toString() {
            return "CallStack{" +
                   "expectedFetchCountPerLevel=" + expectedFetchCountPerLevel +
                   ", fetchCountPerLevel=" + happenedFetchCountPerLevel +
//                   ", expectedExecuteObjectCallsPerLevel=" + expectedExecuteObjectCallsPerLevel +
//                   ", happenedExecuteObjectCallsPerLevel=" + happenedExecuteObjectCallsPerLevel +
//                   ", happenedOnFieldValueCallsPerLevel=" + happenedOnFieldValueCallsPerLevel +
//                   ", dispatchedLevels" + dispatchedLevels +
                   '}';
        }


//        public void setDispatchedLevel(int level) {
//            if (!dispatchedLevels.add(level)) {
//                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
//            }
//        }

//        public void clearHappenedCompleteFields() {
//            this.happenedCompleteFieldPerLevel.clear();
//
//        }
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
        increaseExpectedFetchCount(1, fieldCount, initialCallStack);
    }

    @Override
    public void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        resetCallStack(callStack);
        // field count is always 1 for serial execution
        increaseExpectedFetchCount(1, 1, callStack);
    }

    private CallStack getCallStack(ExecutionStrategyParameters parameters) {
        return getCallStack(parameters.getDeferredCallContext());
    }

    private CallStack getCallStack(@Nullable AlternativeCallContext alternativeCallContext) {
        if (alternativeCallContext == null) {
            return this.initialCallStack;
        } else {
            return alternativeCallContextMap.computeIfAbsent(alternativeCallContext, k -> {
                // currently only works for subscriptions
                CallStack callStack = new CallStack();
//                System.out.println("new callstack: " + callStack);
                int startLevel = alternativeCallContext.getStartLevel();
                int fields = alternativeCallContext.getFields();
                callStack.expectedFetchCountPerLevel.set(1, 1);
                callStack.happenedFetchCountPerLevel.set(1, 1);
                return callStack;
            });
        }
    }

    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {
        CallStack callStack = getCallStack(parameters);
        int curLevel = parameters.getPath().getLevel();
        increaseExpectedFetchCount(curLevel + 1, fieldCount, callStack);
    }


    @Override
    public void newSubscriptionExecution(FieldValueInfo fieldValueInfo, AlternativeCallContext alternativeCallContext) {
        CallStack callStack = getCallStack(alternativeCallContext);
//        callStack.increaseHappenedFetchCount(1);
        callStack.deferredFragmentRootFieldsFetched.add(fieldValueInfo);
    }

//    @Override
//    public void deferredOnFieldValue(String resultKey, FieldValueInfo fieldValueInfo, Throwable
//            throwable, ExecutionStrategyParameters parameters) {
//        CallStack callStack = getCallStack(parameters);
//        boolean ready;
//        synchronized (callStack) {
//            callStack.deferredFragmentRootFieldsFetched.add(fieldValueInfo);
//            Assert.assertNotNull(parameters.getDeferredCallContext());
//            ready = callStack.deferredFragmentRootFieldsFetched.size() == parameters.getDeferredCallContext().getFields();
//        }

    /// /        if (ready) {
    /// /            int curLevel = parameters.getPath().getLevel();
    /// /            onFieldValuesInfoDispatchIfNeeded(callStack.deferredFragmentRootFieldsFetched, curLevel, callStack);
    /// /        }
//    }
    private void increaseExpectedFetchCount(int curLevel,
                                            int fieldCount,
                                            CallStack callStack) {
        while (true) {
            CallStack.StateForLevel currentState = callStack.get(curLevel);
            CallStack.StateForLevel newState = new CallStack.StateForLevel(currentState.dispatched,
                    currentState.expectedFetchCount + fieldCount,
                    currentState.happenedFetchCount,
                    currentState.happenedCompleteFieldCount);
            if (callStack.tryUpdateLevel(curLevel, currentState, newState)) {
                return;
            }
        }
    }

    private void resetCallStack(CallStack callStack) {
//        synchronized (callStack) {
//            callStack.clearDispatchLevels();
        callStack.clear();
//            callStack.clearHappenedCompleteFields();
//            callStack.clearExpectedFetchCount();
//            callStack.clearHappenedFetchCount();
        callStack.chainedDLStack.clear();
//        }
    }


    @Override
    public void fieldCompleted(FieldValueInfo fieldValueInfo, ExecutionStrategyParameters parameters) {
        int level = parameters.getPath().getLevel();
//        System.out.println("field completed at level: " + level + " at: " + parameters.getPath());
        CallStack callStack = getCallStack(parameters);
        increaseHappenedFieldCompleteCount(level, callStack);
        int currentLevel = parameters.getPath().getLevel() + 1;
        // check the levels below if they are ready for dispatch
        while (true) {
            if (!markAsDispatchedIfReady(currentLevel, callStack)) {
                break;
            }
            dispatch(currentLevel, callStack);
            currentLevel++;
        }
    }


    @Override
    public void fieldFetched(ExecutionContext executionContext,
                             ExecutionStrategyParameters executionStrategyParameters,
                             DataFetcher<?> dataFetcher,
                             Object fetchedValue,
                             Supplier<DataFetchingEnvironment> dataFetchingEnvironment) {
        CallStack callStack = getCallStack(executionStrategyParameters);
        int level = executionStrategyParameters.getPath().getLevel();
        increaseHappenedFetchCount(level, callStack);
        if (markAsDispatchedIfReady(level, callStack)) {
            dispatch(level, callStack);
        }

    }

    private void increaseHappenedFieldCompleteCount(int level, CallStack callStack) {
        while (true) {
            CallStack.StateForLevel currentState = callStack.get(level);
            CallStack.StateForLevel newState = new CallStack.StateForLevel(currentState.dispatched,
                    currentState.expectedFetchCount,
                    currentState.happenedFetchCount,
                    currentState.happenedCompleteFieldCount + 1);
            if (callStack.tryUpdateLevel(level, currentState, newState)) {
                return;
            }
        }
    }

    private void increaseHappenedFetchCount(int level, CallStack callStack) {
        while (true) {
            CallStack.StateForLevel currentState = callStack.get(level);
            CallStack.StateForLevel newState = new CallStack.StateForLevel(currentState.dispatched,
                    currentState.expectedFetchCount,
                    currentState.happenedFetchCount + 1,
                    currentState.happenedCompleteFieldCount);
            if (callStack.tryUpdateLevel(level, currentState, newState)) {
                return;
            }
        }
    }


    private boolean markAsDispatchedIfReady(int level, CallStack callStack) {
        boolean previousLevelDispatched = true;
        boolean previousLevelAllFieldsCompleted = true;
        if (level > 1) {
            CallStack.StateForLevel stateForLevel = callStack.get(level - 1);
            previousLevelDispatched = stateForLevel.dispatched;
            previousLevelAllFieldsCompleted = stateForLevel.allFieldsCompleted();
        }
        if (!previousLevelDispatched || !previousLevelAllFieldsCompleted) {
            return false;
        }
        while (true) {
            CallStack.StateForLevel currentState = callStack.get(level);
            if (currentState.dispatched) {
                return false;
            }
            if (!currentState.allFetchesHappened()) {
                return false;
            }
            CallStack.StateForLevel newState = new CallStack.StateForLevel(true,
                    currentState.expectedFetchCount,
                    currentState.happenedFetchCount,
                    currentState.happenedCompleteFieldCount);
            if (callStack.tryUpdateLevel(level, currentState, newState)) {
                return true;
            }
        }
    }

    //
// thread safety : called with callStack.lock
//
//    private boolean dispatchIfNeeded(int level, CallStack callStack) {
//        boolean ready = checkLevelImpl(level, callStack);
//        if (ready) {
//            callStack.setDispatchedLevel(level);
//            return true;
//        }
//        return false;
//    }


//    private boolean checkLevelImpl(int level, CallStack callStack) {
//        System.out.println("checkLevelImpl " + level);
//        // a level with zero expectations can't be ready
//        if (callStack.expectedFetchCountPerLevel.get(level) == 0) {
//            return false;
//        }
//
//        // all fetches happened
//        if (!callStack.allFetchesHappened(level)) {
//            return false;
//        }
//
//        int levelTmp = level - 1;
//        while (levelTmp >= 1) {
//            if (!callStack.allFieldsCompleted(levelTmp)) {
//                return false;
//            }
//            levelTmp--;
//        }
//        System.out.println("check ready " + level);
//        return true;
//    }

    void dispatch(int level, CallStack callStack) {
        if (!enableDataLoaderChaining) {
            profiler.oldStrategyDispatchingAll(level);
            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            dispatchAll(dataLoaderRegistry, level);
            return;
        }
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

