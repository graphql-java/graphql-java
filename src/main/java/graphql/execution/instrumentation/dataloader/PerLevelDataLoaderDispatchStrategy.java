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
import java.util.concurrent.atomic.AtomicInteger;
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


                boolean dispatchingStarted = false;
                boolean dispatchingFinished = false;
                boolean currentlyDelayedDispatching = false;

                if (currentState != null) {
                    dispatchingStarted = currentState.dispatchingStarted;
                    dispatchingFinished = currentState.dispatchingFinished;
                    currentlyDelayedDispatching = currentState.currentlyDelayedDispatching;

                }

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

                boolean dispatchingStarted = false;
                boolean dispatchingFinished = false;
                boolean currentlyDelayedDispatching = false;

                if (currentState != null) {
                    dispatchingStarted = currentState.dispatchingStarted;
                    dispatchingFinished = currentState.dispatchingFinished;
                    currentlyDelayedDispatching = currentState.currentlyDelayedDispatching;

                }

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
         * We track three things per level:
         * - the number of execute object calls
         * - the number of object completion calls
         * - if the level is already dispatched
         * <p/>
         * The number of execute object calls is the number of times the execution
         * of a field with sub selection (meaning it is an object) started.
         * <p/>
         * For each execute object call there will be one matching object completion call,
         * indicating that the all fields in the sub selection have been fetched AND completed.
         * Completion implies the fetched value is "resolved" (CompletableFuture is completed if it was a CF)
         * and it the engine has processed it and called any needed subsequent execute object calls (if the result
         * was none null and of Object of [Object] (or [[Object]] etc).
         * <p/>
         * Together we know a that a level is ready for dispatch if:
         * - the parent was dispatched
         * - the #executeObject == #completionFinished in the grandparent level.
         * <p/>
         * The second condition implies that all execute object calls in the parent level happened
         * which again implies that all fetch fields in the current level have happened.
         * <p/>
         * For the first level we track only if all expected fetched field calls have happened.
         */

        /**
         * The whole algo is impleted lock free and relies purely on CAS methods to handle concurrency.
         */

        static class StateForLevel {
            private final int happenedCompletionFinishedCount;
            private final int happenedExecuteObjectCalls;


            public StateForLevel() {
                this.happenedCompletionFinishedCount = 0;
                this.happenedExecuteObjectCalls = 0;
            }

            public StateForLevel(int happenedCompletionFinishedCount, int happenedExecuteObjectCalls) {
                this.happenedCompletionFinishedCount = happenedCompletionFinishedCount;
                this.happenedExecuteObjectCalls = happenedExecuteObjectCalls;
            }

            public StateForLevel(StateForLevel other) {
                this.happenedCompletionFinishedCount = other.happenedCompletionFinishedCount;
                this.happenedExecuteObjectCalls = other.happenedExecuteObjectCalls;
            }

            public StateForLevel copy() {
                return new StateForLevel(this);
            }

            public StateForLevel increaseHappenedCompletionFinishedCount() {
                return new StateForLevel(happenedCompletionFinishedCount + 1, happenedExecuteObjectCalls);
            }

            public StateForLevel increaseHappenedExecuteObjectCalls() {
                return new StateForLevel(happenedCompletionFinishedCount, happenedExecuteObjectCalls + 1);
            }

        }

        private volatile int expectedFirstLevelFetchCount;
        private final AtomicInteger happenedFirstLevelFetchCount = new AtomicInteger();


        private final Map<Integer, AtomicReference<StateForLevel>> stateForLevelMap = new ConcurrentHashMap<>();

        private final Set<Integer> dispatchedLevels = ConcurrentHashMap.newKeySet();

        public ChainedDLStack chainedDLStack = new ChainedDLStack();

        private final AtomicInteger deferredFragmentRootFieldsCompleted = new AtomicInteger();

        public CallStack() {
        }


        public StateForLevel get(int level) {
            AtomicReference<StateForLevel> dataPerLevelAtomicReference = stateForLevelMap.computeIfAbsent(level, __ -> new AtomicReference<>(new StateForLevel()));
            return Assert.assertNotNull(dataPerLevelAtomicReference.get());
        }

        public boolean tryUpdateLevel(int level, StateForLevel oldData, StateForLevel newData) {
            AtomicReference<StateForLevel> dataPerLevelAtomicReference = Assert.assertNotNull(stateForLevelMap.get(level));
            return dataPerLevelAtomicReference.compareAndSet(oldData, newData);
        }


        public void clear() {
            dispatchedLevels.clear();
            stateForLevelMap.clear();
            expectedFirstLevelFetchCount = 0;
            happenedFirstLevelFetchCount.set(0);
            deferredFragmentRootFieldsCompleted.set(0);
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
        // no concurrency access happening
        CallStack.StateForLevel currentState = initialCallStack.get(0);
        initialCallStack.tryUpdateLevel(0, currentState, new CallStack.StateForLevel(0, 1));
        initialCallStack.expectedFirstLevelFetchCount = fieldCount;
    }

    @Override
    public void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        callStack.clear();
        CallStack.StateForLevel currentState = initialCallStack.get(0);
        initialCallStack.tryUpdateLevel(0, currentState, new CallStack.StateForLevel(0, 1));
        // field count is always 1 for serial execution
        initialCallStack.expectedFirstLevelFetchCount = 1;
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
        while (true) {
            CallStack.StateForLevel currentState = callStack.get(curLevel);
            if (callStack.tryUpdateLevel(curLevel, currentState, currentState.increaseHappenedExecuteObjectCalls())) {
                return;
            }
        }
    }

    @Override
    public void executeObjectOnFieldValuesInfo(List<FieldValueInfo> fieldValueInfoList, ExecutionStrategyParameters parameters) {
        int curLevel = parameters.getPath().getLevel();
        CallStack callStack = getCallStack(parameters);
        onCompletionFinished(curLevel, callStack);
    }

    @Override
    public void executeObjectOnFieldValuesException(Throwable t, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        int curLevel = parameters.getPath().getLevel();
        onCompletionFinished(curLevel, callStack);
    }


    private void onCompletionFinished(int level, CallStack callStack) {
        while (true) {
            CallStack.StateForLevel currentState = callStack.get(level);
            if (callStack.tryUpdateLevel(level, currentState, currentState.increaseHappenedCompletionFinishedCount())) {
                break;
            }
        }

        // due to synchronous DataFetcher the completion calls on higher levels
        // can happen before the completion calls on lower level
        // this means sometimes a lower level completion means multiple levels are ready
        // hence this loop here until a level is not ready or already dispatched
        int currentLevel = level + 2;
        while (true) {
            boolean levelReady;
            if (callStack.dispatchedLevels.contains(currentLevel)) {
                break;
            }
            levelReady = markLevelAsDispatchedIfReady(currentLevel, callStack);
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
        AlternativeCallContext deferredCallContext = executionStrategyParameters.getDeferredCallContext();
        if (level == 1 || (deferredCallContext != null && level == deferredCallContext.getStartLevel())) {
            int happenedFirstLevelFetchCount = callStack.happenedFirstLevelFetchCount.incrementAndGet();
            if (happenedFirstLevelFetchCount == callStack.expectedFirstLevelFetchCount) {
                callStack.dispatchedLevels.add(level);
                dispatch(level, callStack);
            }
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
        callStack.dispatchedLevels.add(1);
        while (true) {
            CallStack.StateForLevel currentState = callStack.get(0);
            if (callStack.tryUpdateLevel(0, currentState, currentState.increaseHappenedExecuteObjectCalls())) {
                break;
            }
        }
        onCompletionFinished(0, callStack);
    }

    @Override
    public void deferredOnFieldValue(String resultKey, FieldValueInfo fieldValueInfo, Throwable throwable, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        int deferredFragmentRootFieldsCompleted = callStack.deferredFragmentRootFieldsCompleted.incrementAndGet();
        Assert.assertNotNull(parameters.getDeferredCallContext());
        if (deferredFragmentRootFieldsCompleted == parameters.getDeferredCallContext().getFields()) {
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
                    CallStack.StateForLevel stateForLevel = callStack.get(startLevel - 2);
                    CallStack.StateForLevel newStateForLevel = stateForLevel.increaseHappenedExecuteObjectCalls().increaseHappenedCompletionFinishedCount();
                    callStack.tryUpdateLevel(startLevel - 2, stateForLevel, newStateForLevel);
                }
                // the parent will have one completion therefore we set the expectation to 1
                CallStack.StateForLevel stateForLevel = callStack.get(startLevel - 1);
                callStack.tryUpdateLevel(startLevel - 1, stateForLevel, stateForLevel.increaseHappenedExecuteObjectCalls());

                // for the current level we set the fetch expectations
                callStack.expectedFirstLevelFetchCount = fields;
                return callStack;
            });
        }
    }


    private boolean markLevelAsDispatchedIfReady(int level, CallStack callStack) {
        boolean ready = isLevelReady(level, callStack);
        if (ready) {
            if (!callStack.dispatchedLevels.add(level)) {
                // meaning another thread came before us, so they will take care of dispatching
                return false;
            }
            return true;
        }
        return false;
    }


    private boolean isLevelReady(int level, CallStack callStack) {
        Assert.assertTrue(level > 1);
        // we expect that parent has been dispatched and that all parents fields are completed
        // all parent fields completed means all parent parent on completions finished calls must have happened
        int happenedExecuteObjectCalls = callStack.get(level - 2).happenedExecuteObjectCalls;
        return callStack.dispatchedLevels.contains(level - 1) &&
               happenedExecuteObjectCalls > 0 && happenedExecuteObjectCalls == callStack.get(level - 2).happenedCompletionFinishedCount;

    }

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

