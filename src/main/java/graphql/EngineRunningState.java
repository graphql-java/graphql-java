package graphql;

import graphql.execution.AbortExecutionException;
import graphql.execution.EngineRunningObserver;
import graphql.execution.ExecutionId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static graphql.Assert.assertTrue;
import static graphql.execution.EngineRunningObserver.RunningState.CANCELLED;
import static graphql.execution.EngineRunningObserver.RunningState.NOT_RUNNING;
import static graphql.execution.EngineRunningObserver.RunningState.NOT_RUNNING_FINISH;
import static graphql.execution.EngineRunningObserver.RunningState.RUNNING;
import static graphql.execution.EngineRunningObserver.RunningState.RUNNING_START;

@Internal
@NullMarked
public class EngineRunningState {

    @Nullable
    private final EngineRunningObserver engineRunningObserver;
    private volatile ExecutionInput executionInput;
    private final GraphQLContext graphQLContext;
    private volatile ExecutionId executionId;

    // if true the last decrementRunning() call will be ignored
    private volatile boolean finished;

    private final AtomicInteger isRunning = new AtomicInteger(0);

    @VisibleForTesting
    public EngineRunningState() {
        this.engineRunningObserver = null;
        this.graphQLContext = null;
        this.executionId = null;
    }

    public EngineRunningState(ExecutionInput executionInput) {
        this.executionInput = executionInput;
        this.graphQLContext = executionInput.getGraphQLContext();
        this.executionId = executionInput.getExecutionId();
        this.engineRunningObserver = executionInput.getGraphQLContext().get(EngineRunningObserver.ENGINE_RUNNING_OBSERVER_KEY);
    }

    public EngineRunningState(ExecutionInput executionInput, Profiler profiler) {
        EngineRunningObserver engineRunningObserver = executionInput.getGraphQLContext().get(EngineRunningObserver.ENGINE_RUNNING_OBSERVER_KEY);
        EngineRunningObserver wrappedObserver = profiler.wrapEngineRunningObserver(engineRunningObserver);
        if (wrappedObserver != null) {
            this.engineRunningObserver = wrappedObserver;
            this.graphQLContext = executionInput.getGraphQLContext();
            this.executionId = executionInput.getExecutionId();
        } else {
            this.engineRunningObserver = null;
            this.graphQLContext = null;
            this.executionId = null;
        }
    }



    public <U, T> CompletableFuture<U> handle(CompletableFuture<T> src, BiFunction<? super T, Throwable, ? extends U> fn) {
        if (engineRunningObserver == null) {
            return src.handle(fn);
        }
        src = observeCompletableFutureStart(src);
        CompletableFuture<U> result = src.handle((t, throwable) -> {
            // because we added an artificial dependent CF on src (in observeCompletableFutureStart) , a throwable is a CompletionException
            // that needs to be unwrapped
            if (throwable != null) {
                throwable = throwable.getCause();
            }
            //noinspection DataFlowIssue
            return fn.apply(t, throwable);
        });
        observerCompletableFutureEnd(src);
        return result;
    }

    public <T> CompletableFuture<T> whenComplete(CompletableFuture<T> src, BiConsumer<? super T, ? super Throwable> fn) {
        if (engineRunningObserver == null) {
            return src.whenComplete(fn);
        }
        src = observeCompletableFutureStart(src);
        CompletableFuture<T> result = src.whenComplete((t, throwable) -> {
            // because we added an artificial dependent CF on src (in observeCompletableFutureStart) , a throwable is a CompletionException
            // that needs to be unwrapped
            if (throwable != null) {
                throwable = throwable.getCause();
            }
            fn.accept(t, throwable);
        });
        observerCompletableFutureEnd(src);
        return result;
    }

    public <U, T> CompletableFuture<U> compose(CompletableFuture<T> src, Function<? super T, ? extends CompletionStage<U>> fn) {
        if (engineRunningObserver == null) {
            return src.thenCompose(fn);
        }
        CompletableFuture<U> result = new CompletableFuture<>();
        src = observeCompletableFutureStart(src);
        src.whenComplete((u, t) -> {
            CompletionStage<U> innerCF;
            try {
                innerCF = fn.apply(u).toCompletableFuture();
            } catch (Throwable e) {
                innerCF = CompletableFuture.failedFuture(e);
            }
            // this run is needed to wrap around the result.complete()/result.completeExceptionally() call
            innerCF.whenComplete((u1, t1) -> run(() -> {
                if (t1 != null) {
                    result.completeExceptionally(t1);
                } else {
                    result.complete(u1);
                }
            }));
        });
        observerCompletableFutureEnd(src);
        return result;
    }


    private <T> CompletableFuture<T> observeCompletableFutureStart(CompletableFuture<T> future) {
        if (engineRunningObserver == null) {
            return future;
        }
        // the completion order of dependent CFs is in stack order for
        // directly dependent CFs, but in reverse stack order for indirect dependent ones
        // By creating one dependent CF on originalFetchValue, we make sure the order it is always
        // in reverse stack order
        future = future.thenApply(Function.identity());
        incrementRunningWhenCompleted(future);
        return future;
    }

    private void observerCompletableFutureEnd(CompletableFuture<?> future) {
        if (engineRunningObserver == null) {
            return;
        }
        decrementRunningWhenCompleted(future);
    }


    private void incrementRunningWhenCompleted(CompletableFuture<?> cf) {
        cf.whenComplete((result, throwable) -> {
            incrementRunning();
        });
    }

    private void decrementRunningWhenCompleted(CompletableFuture<?> cf) {
        cf.whenComplete((result, throwable) -> {
            decrementRunning();
        });

    }

    private void decrementRunning() {
        if (engineRunningObserver == null) {
            return;
        }
        assertTrue(isRunning.get() > 0);
        if (isRunning.decrementAndGet() == 0 && !finished) {
            changeOfState(NOT_RUNNING);
        }
    }


    private void incrementRunning() {
        if (engineRunningObserver == null) {
            return;
        }
        assertTrue(isRunning.get() >= 0);
        if (isRunning.incrementAndGet() == 1) {
            changeOfState(RUNNING);
        }

    }


    public void updateExecutionInput(ExecutionInput executionInput) {
        this.executionInput = executionInput;
        this.executionId = executionInput.getExecutionId();
    }

    private void changeOfState(EngineRunningObserver.RunningState runningState) {
        if (engineRunningObserver != null) {
            engineRunningObserver.runningStateChanged(executionId, graphQLContext, runningState);
        }
    }

    private void run(Runnable runnable) {
        if (engineRunningObserver == null) {
            runnable.run();
            return;
        }
        incrementRunning();
        try {
            runnable.run();
        } finally {
            decrementRunning();
        }
    }

    /**
     * Only used once outside of this class: when the execution starts
     */
    public CompletableFuture<ExecutionResult> engineRun(Supplier<CompletableFuture<ExecutionResult>> engineRun) {
        if (engineRunningObserver == null) {
            return engineRun.get();
        }
        isRunning.incrementAndGet();
        changeOfState(RUNNING_START);

        CompletableFuture<ExecutionResult> erCF = engineRun.get();
        erCF = erCF.whenComplete((result, throwable) -> {
            finished = true;
            changeOfState(NOT_RUNNING_FINISH);
        });
        decrementRunning();
        return erCF;
    }


    /**
     * This will abort the execution via throwing {@link AbortExecutionException} if the {@link ExecutionInput} has been cancelled
     */
    public void throwIfCancelled() throws AbortExecutionException {
        AbortExecutionException abortExecutionException = ifCancelledMakeException();
        if (abortExecutionException != null) {
            throw abortExecutionException;
        }
    }

    /**
     * if the passed in {@link Throwable}is non-null then it is returned as id and if there is no exception then
     * the cancellation state is checked in {@link ExecutionInput#isCancelled()} and a {@link AbortExecutionException}
     * is made as the returned {@link Throwable}
     *
     * @param currentThrowable the current exception state
     *
     * @return a current throwable or a cancellation exception or null if none are in error
     */
    @Internal
    @Nullable
    public Throwable possibleCancellation(@Nullable Throwable currentThrowable) {
        // no need to check we are cancelled if we already have an exception in play
        // since it can lead to an exception being thrown when an exception has already been
        // thrown
        if (currentThrowable == null) {
            return ifCancelledMakeException();
        }
        return currentThrowable;
    }

    /**
     * @return a AbortExecutionException if the current operation has been cancelled via {@link ExecutionInput#cancel()}
     */
    public @Nullable AbortExecutionException ifCancelledMakeException() {
        if (executionInput.isCancelled()) {
            changeOfState(CANCELLED);
            return new AbortExecutionException("Execution has been asked to be cancelled");
        }
        return null;
    }

}
