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
        private final LevelMap happenedFetchCountPerLevel = new LevelMap();
        private final LevelMap happenedCompletionFinishedCountPerLevel = new LevelMap();
        private final LevelMap happenedExecuteObjectCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = ConcurrentHashMap.newKeySet();


        public ChainedDLStack chainedDLStack = new ChainedDLStack();

        private final List<FieldValueInfo> deferredFragmentRootFieldsFetched = new ArrayList<>();

        public CallStack() {
        }


        void clearDispatchLevels() {
            dispatchedLevels.clear();
        }

        @Override
        public String toString() {
            return "CallStack{" +
                   "expectedFetchCountPerLevel=" + expectedFetchCountPerLevel +
                   ", fetchCountPerLevel=" + happenedFetchCountPerLevel +
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

        public void clear() {
            dispatchedLevels.clear();
            happenedExecuteObjectCallsPerLevel.clear();
            expectedFetchCountPerLevel.clear();
            happenedFetchCountPerLevel.clear();
            happenedCompletionFinishedCountPerLevel.clear();


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
//        System.out.println("execution strategy started");
        synchronized (initialCallStack) {
            initialCallStack.happenedExecuteObjectCallsPerLevel.set(0, 1);
            initialCallStack.expectedFetchCountPerLevel.set(1, fieldCount);
        }
    }

    @Override
    public void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        resetCallStack(callStack);
        // field count is always 1 for serial execution
        synchronized (callStack) {
            callStack.happenedExecuteObjectCallsPerLevel.set(0, 1);
            callStack.expectedFetchCountPerLevel.set(1, 1);
        }
    }

    @Override
    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
//        System.out.println("1st level fields completed");
        onCompletionFinished(0, callStack);

    }

    @Override
    public void executionStrategyOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        onCompletionFinished(0, callStack);
    }


    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {
        CallStack callStack = getCallStack(parameters);
        int curLevel = parameters.getPath().getLevel();
//        System.out.println("execute object " + curLevel + " at " + parameters.getPath() + " with callstack " + callStack.hashCode());
        synchronized (callStack) {
            callStack.happenedExecuteObjectCallsPerLevel.increment(curLevel, 1);
            callStack.expectedFetchCountPerLevel.increment(curLevel + 1, fieldCount);
        }
    }

    @Override
    public void executeObjectOnFieldValuesInfo
            (List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel();
        CallStack callStack = getCallStack(parameters);
//        System.out.println("completion finished at " + curLevel + " at " + parameters.getPath() );
        onCompletionFinished(curLevel, callStack);
    }

    @Override
    public void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        int curLevel = parameters.getPath().getLevel();
        onCompletionFinished(curLevel, callStack);
    }

    private void onCompletionFinished(int level, CallStack callStack) {
        synchronized (callStack) {
            callStack.happenedCompletionFinishedCountPerLevel.increment(level, 1);
        }
        // on completion might mark multiple higher levels as ready
        int currentLevel = level + 2;
        while (true) {
            boolean levelReady;
            synchronized (callStack) {
                if (callStack.dispatchedLevels.contains(currentLevel)) {
                    break;
                }
                levelReady = markLevelAsDispatchedIfReady(currentLevel, callStack);
            }
            if (levelReady) {
                dispatch(currentLevel, callStack);
            } else {
                break;
            }
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
//        System.out.println("field fetched at: " + level + " path: " + executionStrategyParameters.getPath() + " callStack: " + callStack.hashCode());
        boolean dispatchNeeded;
        synchronized (callStack) {
            callStack.happenedFetchCountPerLevel.increment(level, 1);
            dispatchNeeded = markLevelAsDispatchedIfReady(level, callStack);
        }
        if (dispatchNeeded) {
            dispatch(level, callStack);
        }

    }


    @Override
    public void newSubscriptionExecution(AlternativeCallContext alternativeCallContext) {
        CallStack callStack = new CallStack();
        alternativeCallContextMap.put(alternativeCallContext, callStack);

    }

    @Override
    public void subscriptionEventCompletionDone(AlternativeCallContext alternativeCallContext) {
        CallStack callStack = getCallStack(alternativeCallContext);
        // this means the single root field is completed (it was never "fetched" because it is
        // the event payload) and we can mark level 1 (root fields) as dispatched and level 0 as completed
        synchronized (callStack) {
            callStack.dispatchedLevels.add(1);
            callStack.happenedExecuteObjectCallsPerLevel.set(0, 1);
        }
        onCompletionFinished(0, callStack);
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

//

    private CallStack getCallStack(ExecutionStrategyParameters parameters) {
        return getCallStack(parameters.getDeferredCallContext());
    }

    private CallStack getCallStack(@Nullable AlternativeCallContext alternativeCallContext) {
        if (alternativeCallContext == null) {
            return this.initialCallStack;
        } else {
            return alternativeCallContextMap.computeIfAbsent(alternativeCallContext, k -> {
                CallStack callStack = new CallStack();
//                System.out.println("new callstack : " + callStack.hashCode());
                // for subscriptions there is only root field which is already fetched
//                callStack.expectedFetchCountPerLevel.set(1, 1);
//                callStack.happenedFetchCountPerLevel.set(1, 1);
//                // the level 0 is done
//                callStack.happenedExecuteObjectCallsPerLevel.set(0, 1);
//                callStack.happenedCompletionFinishedCountPerLevel.set(0, 1);
//                // level is 1 already dispatched
//                callStack.setDispatchedLevel(1);
//                int startLevel = alternativeCallContext.getStartLevel();
//                int fields = alternativeCallContext.getFields();
                return callStack;
            });
        }
    }


    private void resetCallStack(CallStack callStack) {
        synchronized (callStack) {
            callStack.clear();
            callStack.chainedDLStack.clear();
        }
    }


    private boolean markLevelAsDispatchedIfReady(int level, CallStack callStack) {
        boolean ready = isLevelReady(level, callStack);
//        System.out.println("markLevelAsDispatchedIfReady level: " + level + " ready: "  + ready);
        if (ready) {
            callStack.setDispatchedLevel(level);
            return true;
        }
        return false;
    }


    private boolean isLevelReady(int level, CallStack callStack) {
        // a level with zero expectations can't be ready
        int expectedFetchCount = callStack.expectedFetchCountPerLevel.get(level);
        if (expectedFetchCount == 0) {
            return false;
        }

        if (expectedFetchCount != callStack.happenedFetchCountPerLevel.get(level)) {
            return false;
        }
        if (level == 1) {
            // for the root fields we just expect that they were all fetched
            return true;
        }

        // we expect that parent has been dispatched and that all parents fields are completed
        // all parent fields completed means all parent parent on completions finished calls must have happened
        return callStack.dispatchedLevels.contains(level - 1) &&
               callStack.happenedExecuteObjectCallsPerLevel.get(level - 2) == callStack.happenedCompletionFinishedCountPerLevel.get(level - 2);

    }

    void dispatch(int level, CallStack callStack) {
//        System.out.println("dispatching at " + level);
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

