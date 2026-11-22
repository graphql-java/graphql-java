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
import graphql.VisibleForTesting;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Internal
@NullMarked
public class PerLevelDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    @VisibleForTesting
    final CallStack initialCallStack;
    private final ExecutionContext executionContext;
    private final boolean enableDataLoaderChaining;


    private final Profiler profiler;

    private final Map<AlternativeCallContext, CallStack> alternativeCallContextMap = new ConcurrentHashMap<>();

    private static class ChainedDLStack {

        private final Map<Integer, AtomicReference<@Nullable StateForLevel>> stateMapPerLevel = new ConcurrentHashMap<>();

        // a state for level points to a previous one
        // all the invocations that are linked together are the relevant invocations for the next dispatch
        private static class StateForLevel {
            final @Nullable DataLoader dataLoader;
            final boolean dispatchingStarted;
            final boolean dispatchingFinished;
            final boolean currentlyDelayedDispatching;
            final @Nullable StateForLevel prev;

            public StateForLevel(@Nullable DataLoader dataLoader,
                                 boolean dispatchingStarted,
                                 boolean dispatchingFinished,
                                 boolean currentlyDelayedDispatching,
                                 @Nullable StateForLevel prev) {
                this.dataLoader = dataLoader;
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

                if (currentState == null || currentState.dataLoader == null) {
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


        public boolean newDataLoaderInvocation(int level, DataLoader dataLoader) {
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

                StateForLevel newState = new StateForLevel(dataLoader, dispatchingStarted, dispatchingFinished, currentlyDelayedDispatching, currentState);

                if (currentStateRef.compareAndSet(currentState, newState)) {
                    return newDelayedInvocation;
                }
            }
        }

        public void clear() {
            stateMapPerLevel.clear();
        }

    }

    // package-private for testing
    static class CallStack {

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
            @VisibleForTesting
            final int happenedCompletionFinishedCount;
            @VisibleForTesting
            final int happenedExecuteObjectCalls;


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


        /*
         * The two counters of a level packed into one long: happenedCompletionFinishedCount in the
         * high 32 bits, happenedExecuteObjectCalls in the low 32 bits. Both are read in one
         * consistent snapshot, and each increment is a single atomic add instead of a
         * compare-and-set retry loop that allocates a new StateForLevel.
         *
         * Levels are indexed directly in an array that grows on demand (query depth bounds it);
         * the slow path that creates a level is synchronized, the hot path is a volatile read.
         */
        private static final long COMPLETION_FINISHED_ONE = 1L << 32;
        private static final int INITIAL_LEVELS = 16;

        private volatile AtomicLong[] stateForLevels = new AtomicLong[INITIAL_LEVELS];

        @VisibleForTesting
        final Set<Integer> dispatchedLevels = ConcurrentHashMap.newKeySet();

        public ChainedDLStack chainedDLStack = new ChainedDLStack();

        private final AtomicInteger deferredFragmentRootFieldsCompleted = new AtomicInteger();

        public CallStack() {
        }


        private AtomicLong stateForLevel(int level) {
            AtomicLong[] levels = stateForLevels;
            if (level < levels.length) {
                AtomicLong state = levels[level];
                if (state != null) {
                    return state;
                }
            }
            return createStateForLevel(level);
        }

        private synchronized AtomicLong createStateForLevel(int level) {
            AtomicLong[] levels = stateForLevels;
            if (level >= levels.length) {
                levels = Arrays.copyOf(levels, Math.max(level + 1, levels.length * 2));
            }
            AtomicLong state = levels[level];
            if (state == null) {
                state = new AtomicLong();
                levels[level] = state;
            }
            stateForLevels = levels;
            return state;
        }

        private static long pack(StateForLevel state) {
            return ((long) state.happenedCompletionFinishedCount << 32) | (state.happenedExecuteObjectCalls & 0xFFFFFFFFL);
        }

        private static int completionFinishedCount(long packed) {
            return (int) (packed >>> 32);
        }

        private static int executeObjectCalls(long packed) {
            return (int) packed;
        }

        public StateForLevel get(int level) {
            long packed = stateForLevel(level).get();
            return new StateForLevel(completionFinishedCount(packed), executeObjectCalls(packed));
        }

        public boolean tryUpdateLevel(int level, StateForLevel oldData, StateForLevel newData) {
            return stateForLevel(level).compareAndSet(pack(oldData), pack(newData));
        }

        void increaseHappenedExecuteObjectCalls(int level) {
            stateForLevel(level).getAndIncrement();
        }

        void increaseHappenedCompletionFinishedCount(int level) {
            stateForLevel(level).getAndAdd(COMPLETION_FINISHED_ONE);
        }

        /**
         * @return true if at least one execute object call happened on the level and every one of
         * them has a matching completion, read from a single snapshot of both counters
         */
        boolean allExecuteObjectCallsCompleted(int level) {
            long packed = stateForLevel(level).get();
            int happenedExecuteObjectCalls = executeObjectCalls(packed);
            return happenedExecuteObjectCalls > 0 && happenedExecuteObjectCalls == completionFinishedCount(packed);
        }


        public synchronized void clear() {
            dispatchedLevels.clear();
            stateForLevels = new AtomicLong[INITIAL_LEVELS];
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
        callStack.increaseHappenedExecuteObjectCalls(curLevel);
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
        callStack.increaseHappenedCompletionFinishedCount(level);

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
        AlternativeCallContext alternativeCallContext = executionStrategyParameters.getAlternativeCallContext();
        if (level == 1 || (alternativeCallContext != null && level == alternativeCallContext.getStartLevel())) {
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
        callStack.increaseHappenedExecuteObjectCalls(0);
        onCompletionFinished(0, callStack);
    }

    @Override
    public void subscriptionEventExecutionDone(AlternativeCallContext alternativeCallContext) {
        alternativeCallContextMap.remove(alternativeCallContext);
    }

    @Override
    public void deferredOnFieldValue(String resultKey, FieldValueInfo fieldValueInfo, Throwable throwable, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        int deferredFragmentRootFieldsCompleted = callStack.deferredFragmentRootFieldsCompleted.incrementAndGet();
        Assert.assertNotNull(parameters.getAlternativeCallContext());
        if (deferredFragmentRootFieldsCompleted == parameters.getAlternativeCallContext().getFields()) {
            onCompletionFinished(parameters.getAlternativeCallContext().getStartLevel() - 1, callStack);
        }

    }


    private CallStack getCallStack(ExecutionStrategyParameters parameters) {
        return getCallStack(parameters.getAlternativeCallContext());
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
                    // parent level is considered dispatched and all fields completed (meaning the grandparent level has all object completion call happened)
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


    @VisibleForTesting
    boolean markLevelAsDispatchedIfReady(int level, CallStack callStack) {
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
        return callStack.dispatchedLevels.contains(level - 1) && callStack.allExecuteObjectCallsCompleted(level - 2);

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
        dataLoaderRegistry.dispatchAll();
    }

    private void dispatchDLCFImpl(Integer level, CallStack callStack, boolean normalOrDelayed, boolean chained) {

        ChainedDLStack.StateForLevel stateForLevel = callStack.chainedDLStack.aboutToStartDispatching(level, normalOrDelayed, chained);
        if (stateForLevel == null || stateForLevel.dataLoader == null) {
            return;
        }

        List<CompletableFuture> allDispatchedCFs = new ArrayList<>();
        while (stateForLevel != null && stateForLevel.dataLoader != null) {
            CompletableFuture<List> dispatch = stateForLevel.dataLoader.dispatch();
            allDispatchedCFs.add(dispatch);
            stateForLevel = stateForLevel.prev;
        }
        CompletableFuture.allOf(allDispatchedCFs.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) -> {
                    dispatchDLCFImpl(level, callStack, normalOrDelayed, true);
                        }
                );

    }


    public void newDataLoaderInvocation(int level,
                                        DataLoader dataLoader,
                                        @Nullable AlternativeCallContext alternativeCallContext) {
        if (!enableDataLoaderChaining) {
            return;
        }
        CallStack callStack = getCallStack(alternativeCallContext);
        boolean newDelayedInvocation = callStack.chainedDLStack.newDataLoaderInvocation(level, dataLoader);
        if (newDelayedInvocation) {
            dispatchDLCFImpl(level, callStack, false, false);
        }
    }


}
