package graphql;

import graphql.execution.EngineRunningObserver;
import graphql.execution.ExecutionId;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static graphql.Assert.assertTrue;
import static graphql.execution.EngineRunningObserver.RunningState.NOT_RUNNING;
import static graphql.execution.EngineRunningObserver.RunningState.RUNNING;

@Internal
public class EngineRunningState {

    @Nullable
    private final EngineRunningObserver engineRunningObserver;
    @Nullable
    private final GraphQLContext graphQLContext;
    @Nullable
    private volatile ExecutionId executionId;

    private final AtomicInteger isRunning = new AtomicInteger(0);

    @VisibleForTesting
    public EngineRunningState() {
        this.engineRunningObserver = null;
        this.graphQLContext = null;
        this.executionId = null;
    }

    public EngineRunningState(ExecutionInput executionInput) {
        EngineRunningObserver engineRunningObserver = executionInput.getGraphQLContext().get(EngineRunningObserver.ENGINE_RUNNING_OBSERVER_KEY);
        if (engineRunningObserver != null) {
            this.engineRunningObserver = engineRunningObserver;
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
        if (isRunning.decrementAndGet() == 0) {
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


    public void updateExecutionId(ExecutionId executionId) {
        if (engineRunningObserver == null) {
            return;
        }
        this.executionId = executionId;
    }

    private void changeOfState(EngineRunningObserver.RunningState runningState) {
        engineRunningObserver.runningStateChanged(executionId, graphQLContext, runningState);
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
    public <T> T call(Supplier<T> supplier) {
        if (engineRunningObserver == null) {
            return supplier.get();
        }
        incrementRunning();
        try {
            return supplier.get();
        } finally {
            decrementRunning();
        }
    }


}
