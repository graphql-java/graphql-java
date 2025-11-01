package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.Profiler;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.incremental.AlternativeCallContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Internal
@NullMarked
public class ExhaustedDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final CallStack initialCallStack;
    private final ExecutionContext executionContext;

    private final Profiler profiler;

    private final Map<AlternativeCallContext, CallStack> alternativeCallContextMap = new ConcurrentHashMap<>();


    private static class CallStack {

        // 30 bits for objectRunningCount
        // 1 bit for dataLoaderToDispatch
        // 1 bit for currentlyDispatching

        // Bit positions (from right to left)
        static final int currentlyDispatchingShift = 0;
        static final int dataLoaderToDispatchShift = 1;
        static final int objectRunningCountShift = 2;

        // mask
        static final int booleanMask = 1;
        static final int objectRunningCountMask = (1 << 30) - 1;

        public static int getObjectRunningCount(int state) {
            return (state >> objectRunningCountShift) & objectRunningCountMask;
        }

        public static int setObjectRunningCount(int state, int objectRunningCount) {
            return (state & ~(objectRunningCountMask << objectRunningCountShift)) |
                   (objectRunningCount << objectRunningCountShift);
        }

        public static int setDataLoaderToDispatch(int state, boolean dataLoaderToDispatch) {
            return (state & ~(booleanMask << dataLoaderToDispatchShift)) |
                   ((dataLoaderToDispatch ? 1 : 0) << dataLoaderToDispatchShift);
        }

        public static int setCurrentlyDispatching(int state, boolean currentlyDispatching) {
            return (state & ~(booleanMask << currentlyDispatchingShift)) |
                   ((currentlyDispatching ? 1 : 0) << currentlyDispatchingShift);
        }


        public static boolean getDataLoaderToDispatch(int state) {
            return ((state >> dataLoaderToDispatchShift) & booleanMask) != 0;
        }

        public static boolean getCurrentlyDispatching(int state) {
            return ((state >> currentlyDispatchingShift) & booleanMask) != 0;
        }


        public int incrementObjectRunningCount() {
            while (true) {
                int oldState = getState();
                int objectRunningCount = getObjectRunningCount(oldState);
                int newState = setObjectRunningCount(oldState, objectRunningCount + 1);
                if (tryUpdateState(oldState, newState)) {
                    return newState;
                }
            }
        }

        public int decrementObjectRunningCount() {
            while (true) {
                int oldState = getState();
                int objectRunningCount = getObjectRunningCount(oldState);
                int newState = setObjectRunningCount(oldState, objectRunningCount - 1);
                if (tryUpdateState(oldState, newState)) {
                    return newState;
                }
            }
        }

        // for debugging
        public static String printState(int state) {
            return "objectRunningCount: " + getObjectRunningCount(state) +
                   ",dataLoaderToDispatch: " + getDataLoaderToDispatch(state) +
                   ",currentlyDispatching: " + getCurrentlyDispatching(state);
        }

        private final AtomicInteger state = new AtomicInteger();

        public int getState() {
            return state.get();
        }

        public boolean tryUpdateState(int oldState, int newState) {
            return state.compareAndSet(oldState, newState);
        }

        private final AtomicInteger deferredFragmentRootFieldsCompleted = new AtomicInteger();

        public CallStack() {
        }


        public void clear() {
            deferredFragmentRootFieldsCompleted.set(0);
            state.set(0);
        }
    }

    public ExhaustedDataLoaderDispatchStrategy(ExecutionContext executionContext) {
        this.initialCallStack = new CallStack();
        this.executionContext = executionContext;

        this.profiler = executionContext.getProfiler();
    }


    @Override
    public void executionStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {
        Assert.assertTrue(parameters.getExecutionStepInfo().getPath().isRootPath());
        initialCallStack.incrementObjectRunningCount();
    }

    @Override
    public void finishedFetching(ExecutionContext executionContext, ExecutionStrategyParameters newParameters) {
        CallStack callStack = getCallStack(newParameters);
        decrementObjectRunningAndMaybeDispatch(callStack);
    }

    @Override
    public void executionSerialStrategy(ExecutionContext executionContext, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        callStack.clear();
        callStack.incrementObjectRunningCount();
    }

    @Override
    public void newSubscriptionExecution(AlternativeCallContext alternativeCallContext) {
        CallStack callStack = new CallStack();
        alternativeCallContextMap.put(alternativeCallContext, callStack);
        callStack.incrementObjectRunningCount();
    }

    @Override
    public void subscriptionEventCompletionDone(AlternativeCallContext alternativeCallContext) {
        CallStack callStack = getCallStack(alternativeCallContext);
        decrementObjectRunningAndMaybeDispatch(callStack);
    }

    @Override
    public void deferFieldFetched(ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        int deferredFragmentRootFieldsCompleted = callStack.deferredFragmentRootFieldsCompleted.incrementAndGet();
        Assert.assertNotNull(parameters.getDeferredCallContext());
        if (deferredFragmentRootFieldsCompleted == parameters.getDeferredCallContext().getFields()) {
            decrementObjectRunningAndMaybeDispatch(callStack);
        }
    }

    @Override
    public void startComplete(ExecutionStrategyParameters parameters) {
        getCallStack(parameters).incrementObjectRunningCount();
    }

    @Override
    public void stopComplete(ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        decrementObjectRunningAndMaybeDispatch(callStack);
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
                callStack.incrementObjectRunningCount();
                return callStack;
            });
        }
    }


    private void decrementObjectRunningAndMaybeDispatch(CallStack callStack) {
        int newState = callStack.decrementObjectRunningCount();
        if (CallStack.getObjectRunningCount(newState) == 0 && CallStack.getDataLoaderToDispatch(newState) && !CallStack.getCurrentlyDispatching(newState)) {
            dispatchImpl(callStack);
        }
    }

    private void newDataLoaderInvocationMaybeDispatch(CallStack callStack) {
        int currentState;
        while (true) {
            int oldState = callStack.getState();
            if (CallStack.getDataLoaderToDispatch(oldState)) {
                return;
            }
            int newState = CallStack.setDataLoaderToDispatch(oldState, true);
            if (callStack.tryUpdateState(oldState, newState)) {
                currentState = newState;
                break;
            }
        }

        if (CallStack.getObjectRunningCount(currentState) == 0 && !CallStack.getCurrentlyDispatching(currentState)) {
            dispatchImpl(callStack);
        }
    }


    private void dispatchImpl(CallStack callStack) {
        while (true) {
            int oldState = callStack.getState();
            if (!CallStack.getDataLoaderToDispatch(oldState)) {
                int newState = CallStack.setCurrentlyDispatching(oldState, false);
                if (callStack.tryUpdateState(oldState, newState)) {
                    return;
                }
            }
            int newState = CallStack.setCurrentlyDispatching(oldState, true);
            newState = CallStack.setDataLoaderToDispatch(newState, false);
            if (callStack.tryUpdateState(oldState, newState)) {
                break;
            }
        }

        DataLoaderRegistry dataLoaderRegistry = executionContext.getDataLoaderRegistry();
        List<DataLoader<?, ?>> dataLoaders = dataLoaderRegistry.getDataLoaders();
        List<CompletableFuture<? extends List<?>>> allDispatchedCFs = new ArrayList<>();
        for (DataLoader<?, ?> dataLoader : dataLoaders) {
            CompletableFuture<? extends List<?>> dispatch = dataLoader.dispatch();
            allDispatchedCFs.add(dispatch);
        }
        CompletableFuture.allOf(allDispatchedCFs.toArray(new CompletableFuture[0]))
                .whenComplete((unused, throwable) -> {
                    dispatchImpl(callStack);
                });

    }


    public void newDataLoaderInvocation(@Nullable AlternativeCallContext alternativeCallContext) {
        CallStack callStack = getCallStack(alternativeCallContext);
        newDataLoaderInvocationMaybeDispatch(callStack);
    }


}

