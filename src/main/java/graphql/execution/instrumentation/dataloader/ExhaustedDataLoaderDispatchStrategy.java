package graphql.execution.instrumentation.dataloader;

import graphql.Assert;
import graphql.Internal;
import graphql.Profiler;
import graphql.execution.DataLoaderDispatchStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.FieldValueInfo;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Internal
@NullMarked
public class ExhaustedDataLoaderDispatchStrategy implements DataLoaderDispatchStrategy {

    private final CallStack initialCallStack;
    private final ExecutionContext executionContext;

    private final Profiler profiler;

    private final Map<AlternativeCallContext, CallStack> alternativeCallContextMap = new ConcurrentHashMap<>();


    private static class CallStack {


        static class State {
            final int objectRunningCount;
            final boolean dataLoaderToDispatch;
            final boolean currentlyDispatching;

            State(int objectRunningCount, boolean dataLoaderToDispatch, boolean currentlyDispatching) {
                this.objectRunningCount = objectRunningCount;
                this.dataLoaderToDispatch = dataLoaderToDispatch;
                this.currentlyDispatching = currentlyDispatching;
            }

            public State copy() {
                return new State(objectRunningCount, dataLoaderToDispatch, currentlyDispatching);
            }

            public State incrementObjectRunningCount() {
                return new State(objectRunningCount + 1, dataLoaderToDispatch, currentlyDispatching);
            }

            public State decrementObjetRunningCount() {
                return new State(objectRunningCount - 1, dataLoaderToDispatch, currentlyDispatching);
            }

            public State dataLoaderToDispatch() {
                return new State(objectRunningCount, true, currentlyDispatching);
            }

            public State startDispatching() {
                return new State(objectRunningCount, false, true);
            }

            public State stopDispatching() {
                return new State(objectRunningCount, false, false);
            }


            @Override
            public String toString() {
                return "State{" +
                       "objectRunningCount=" + objectRunningCount +
                       ", dataLoaderToDispatch=" + dataLoaderToDispatch +
                       '}';
            }
        }

        private final AtomicLong state = new AtomicLong();
        private final AtomicReference<State> stateRef = new AtomicReference<>(new State(0, false, false));

        public State getState() {
            return Assert.assertNotNull(stateRef.get());
        }

        public boolean tryUpdateState(State oldState, State newState) {
            System.out.println("updateState: " + oldState + " -> " + newState);
            return stateRef.compareAndSet(oldState, newState);
        }

        private final AtomicInteger deferredFragmentRootFieldsCompleted = new AtomicInteger();

        public CallStack() {
        }


        public void clear() {
            deferredFragmentRootFieldsCompleted.set(0);
            stateRef.set(new State(0, false, false));
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
        // no concurrency access happening
        CallStack.State state = initialCallStack.getState();
        Assert.assertTrue(initialCallStack.tryUpdateState(state, state.incrementObjectRunningCount()));
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
        CallStack.State state = callStack.getState();
        // no concurrency access happening
        Assert.assertTrue(callStack.tryUpdateState(state, state.incrementObjectRunningCount()));
    }


    @Override
    public void executeObject(ExecutionContext executionContext, ExecutionStrategyParameters parameters, int fieldCount) {
        CallStack callStack = getCallStack(parameters);
        while (true) {
            CallStack.State state = callStack.getState();
            if (callStack.tryUpdateState(state, state.incrementObjectRunningCount())) {
                break;
            }
        }
    }


    @Override
    public void newSubscriptionExecution(AlternativeCallContext alternativeCallContext) {
        CallStack callStack = new CallStack();
        alternativeCallContextMap.put(alternativeCallContext, callStack);
    }

    @Override
    public void deferredOnFieldValue(String resultKey, FieldValueInfo fieldValueInfo, Throwable throwable, ExecutionStrategyParameters parameters) {
        CallStack callStack = getCallStack(parameters);
        int deferredFragmentRootFieldsCompleted = callStack.deferredFragmentRootFieldsCompleted.incrementAndGet();
        Assert.assertNotNull(parameters.getDeferredCallContext());
        if (deferredFragmentRootFieldsCompleted == parameters.getDeferredCallContext().getFields()) {
            decrementObjectRunningAndMaybeDispatch(callStack);
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
                return callStack;
            });
        }
    }


    private void decrementObjectRunningAndMaybeDispatch(CallStack callStack) {
        CallStack.State oldState;
        CallStack.State newState;
        while (true) {
            oldState = callStack.getState();
            newState = oldState.decrementObjetRunningCount();
            if (callStack.tryUpdateState(oldState, newState)) {
                break;
            }
        }
        // this means we have not fetching running and we can execute
        if (newState.objectRunningCount == 0 && !newState.currentlyDispatching) {
            dispatchImpl(callStack);
        }
    }

    private void newDataLoaderInvocationMaybeDispatch(CallStack callStack) {
        CallStack.State oldState;
        CallStack.State newState;
        while (true) {
            oldState = callStack.getState();
            newState = oldState.dataLoaderToDispatch();
            if (callStack.tryUpdateState(oldState, newState)) {
                break;
            }
        }
//        System.out.println("new data loader invocation maybe with state: " + newState);
        // this means we are not waiting for some fetching to be finished and we need to dispatch
        if (newState.objectRunningCount == 0 && !newState.currentlyDispatching) {
            dispatchImpl(callStack);
        }

    }


    private void dispatchImpl(CallStack callStack) {

        CallStack.State oldState;
        while (true) {
            oldState = callStack.getState();
            if (!oldState.dataLoaderToDispatch) {
                CallStack.State newState = oldState.stopDispatching();
                if (callStack.tryUpdateState(oldState, newState)) {
                    return;
                }
            }
            CallStack.State newState = oldState.startDispatching();
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


    public void newDataLoaderInvocation(String resultPath,
                                        int level,
                                        DataLoader dataLoader,
                                        String dataLoaderName,
                                        Object key,
                                        @Nullable AlternativeCallContext alternativeCallContext) {
//        DataLoaderInvocation dataLoaderInvocation = new DataLoaderInvocation(resultPath, level, dataLoader, dataLoaderName, key);
        CallStack callStack = getCallStack(alternativeCallContext);
        newDataLoaderInvocationMaybeDispatch(callStack);
    }


}

