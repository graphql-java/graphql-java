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
import java.util.LinkedHashSet;
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

        private final LevelMap expectedFetchCountPerLevel = new LevelMap();
        private final LevelMap happenedFetchCountPerLevel = new LevelMap();
        private final LevelMap happenedCompletionFinishedCountPerLevel = new LevelMap();
        private final LevelMap happenedExecuteObjectCallsPerLevel = new LevelMap();

        private final Set<Integer> dispatchedLevels = new LinkedHashSet<>();


        public ChainedDLStack chainedDLStack = new ChainedDLStack();

        private int deferredFragmentRootFieldsCompleted;

        public CallStack() {
        }


        public void clear() {
            dispatchedLevels.clear();
            happenedExecuteObjectCallsPerLevel.clear();
            expectedFetchCountPerLevel.clear();
            happenedFetchCountPerLevel.clear();
            happenedCompletionFinishedCountPerLevel.clear();
            deferredFragmentRootFieldsCompleted = 0;
            chainedDLStack.clear();
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
        synchronized (initialCallStack) {
            initialCallStack.happenedExecuteObjectCallsPerLevel.set(0, 1);
            initialCallStack.expectedFetchCountPerLevel.set(1, fieldCount);
        }
    }

    @Override
    public void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        callStack.clear();
        synchronized (callStack) {
            callStack.happenedExecuteObjectCallsPerLevel.set(0, 1);
            // field count is always 1 for serial execution
            callStack.expectedFetchCountPerLevel.set(1, 1);
        }
    }

    @Override
    public void executionStrategyOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
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
        onCompletionFinished(curLevel, callStack);
    }

    @Override
    public void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        int curLevel = parameters.getPath().getLevel();
//        System.out.println("completion finished for level " + curLevel);
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
//                        System.out.println("failed because already dispatched");
                    break;
                }
                levelReady = markLevelAsDispatchedIfReady(currentLevel, callStack);
            }
            if (levelReady) {
                dispatch(currentLevel, callStack);
            } else {
//                    System.out.println("failed because level not ready");
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
        boolean dispatchNeeded = false;
//        System.out.println("field fetched for level " + level + " path: " + executionStrategyParameters.getPath());
        AlternativeCallContext deferredCallContext = executionStrategyParameters.getDeferredCallContext();
        if (level == 1 || (deferredCallContext != null && level == deferredCallContext.getStartLevel())) {
            synchronized (callStack) {
                callStack.happenedFetchCountPerLevel.increment(level, 1);
                dispatchNeeded = callStack.expectedFetchCountPerLevel.get(level) == callStack.happenedFetchCountPerLevel.get(level);
                if (dispatchNeeded) {
                    callStack.dispatchedLevels.add(level);
                }
            }
        }
        if (dispatchNeeded) {
//            System.out.println("Success field fetch");
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
            callStack.deferredFragmentRootFieldsCompleted++;
            Assert.assertNotNull(parameters.getDeferredCallContext());
            ready = callStack.deferredFragmentRootFieldsCompleted == parameters.getDeferredCallContext().getFields();
        }
        if (ready) {
            onCompletionFinished(parameters.getDeferredCallContext().getStartLevel() - 1, callStack);
        }

    }


    private CallStack getCallStack(ExecutionStrategyParameters parameters) {
        return getCallStack(parameters.getDeferredCallContext());
    }

    private CallStack getCallStack(@Nullable AlternativeCallContext alternativeCallContext) {
        if (alternativeCallContext == null) {
            return this.initialCallStack;
        } else {
            return alternativeCallContextMap.computeIfAbsent(alternativeCallContext, k -> {
                /*
                  This is only for handling deferred cases. Subscription cases will also get a new callStack, but
                  it is explicitly created in `newSubscriptionExecution`.
                  The reason we are doing this lazily is, because we don't have explicit startDeferred callback.
                 */
                CallStack callStack = new CallStack();
                // on which level the fields are
                int startLevel = k.getStartLevel();
                // how many fields are deferred on this level
                int fields = k.getFields();
                if (startLevel > 1) {
                    // parent level is considered dispatched and all fields completed
                    callStack.dispatchedLevels.add(startLevel - 1);
                    callStack.happenedExecuteObjectCallsPerLevel.set(startLevel - 2, 1);
                    callStack.happenedCompletionFinishedCountPerLevel.set(startLevel - 2, 1);
                }
                // the parent will have one completion therefore we set the expectation to 1
                callStack.happenedExecuteObjectCallsPerLevel.set(startLevel - 1, 1);

                // for the current level we set the fetch expectations
                callStack.expectedFetchCountPerLevel.set(startLevel, fields);
                return callStack;
            });
        }
    }


    private boolean markLevelAsDispatchedIfReady(int level, CallStack callStack) {
        boolean ready = isLevelReady(level, callStack);
        if (ready) {
            if (!callStack.dispatchedLevels.add(level)) {
                Assert.assertShouldNeverHappen("level " + level + " already dispatched");
            }
            return true;
        }
        return false;
    }


    private boolean isLevelReady(int level, CallStack callStack) {
        // a level with zero expectations can't be ready
//            int expectedFetchCount = callStack.expectedFetchCountPerLevel.get(level);
//            if (expectedFetchCount == 0) {
//                return false;
//            }

//            if (expectedFetchCount != callStack.happenedFetchCountPerLevel.get(level)) {
//                return false;
//            }

        // we expect that parent has been dispatched and that all parents fields are completed
        // all parent fields completed means all parent parent on completions finished calls must have happened
        int happenedExecuteObjectCalls = callStack.happenedExecuteObjectCallsPerLevel.get(level - 2);
        return callStack.dispatchedLevels.contains(level - 1) &&
               happenedExecuteObjectCalls > 0 && happenedExecuteObjectCalls == callStack.happenedCompletionFinishedCountPerLevel.get(level - 2);

    }

    void dispatch(int level, CallStack callStack) {
//        System.out.println("dispatching " + level);
        if (!enableDataLoaderChaining) {
            profiler.oldStrategyDispatchingAll(level);
            DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
            dispatchAll(dataLoaderRegistry, level);
            return;
        }
        dispatchDLCFImpl(level, callStack, true, false);
    }

    private void dispatchAll(DataLoaderRegistry dataLoaderRegistry, int level) {
//        System.out.println("dispatch level " + level);
        for (DataLoader<?, ?> dataLoader : dataLoaderRegistry.getDataLoaders()) {
            dataLoader.dispatch().whenComplete((objects, throwable) -> {
                if (objects != null && objects.size() > 0) {
//                    System.out.println("dispatching " + objects.size() + " objects for level " + level);
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

