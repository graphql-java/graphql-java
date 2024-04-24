package graphql.execution.reactive;

import graphql.Internal;
import graphql.util.LockKit;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * This subscriber can be used to map between a {@link org.reactivestreams.Publisher} of U
 * elements and map them into {@link CompletionStage} of D promises.
 *
 * @param <U> published upstream elements
 * @param <D> mapped downstream values
 */
@Internal
public class CompletionStageSubscriber<U, D> implements Subscriber<U> {
    protected final Function<U, CompletionStage<D>> mapper;
    protected final Subscriber<? super D> downstreamSubscriber;
    protected Subscription delegatingSubscription;
    protected final Queue<CompletionStage<?>> inFlightDataQ;
    protected final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();
    protected final AtomicReference<Runnable> onCompleteRun;
    protected final AtomicBoolean isTerminal;

    public CompletionStageSubscriber(Function<U, CompletionStage<D>> mapper, Subscriber<? super D> downstreamSubscriber) {
        this.mapper = mapper;
        this.downstreamSubscriber = downstreamSubscriber;
        inFlightDataQ = new ArrayDeque<>();
        onCompleteRun = new AtomicReference<>();
        isTerminal = new AtomicBoolean(false);
    }

    /**
     * Get instance of downstream subscriber
     *
     * @return {@link Subscriber}
     */
    public Subscriber<? super D> getDownstreamSubscriber() {
        return downstreamSubscriber;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        delegatingSubscription = new DelegatingSubscription(subscription);
        downstreamSubscriber.onSubscribe(delegatingSubscription);
    }

    @Override
    public void onNext(U u) {
        // for safety - no more data after we have called done/error - we should not get this BUT belts and braces
        if (isTerminal()) {
            return;
        }
        try {
            CompletionStage<D> completionStage = mapper.apply(u);
            offerToInFlightQ(completionStage);
            completionStage.whenComplete(whenComplete(completionStage));
        } catch (RuntimeException throwable) {
            handleThrowableDuringMapping(throwable);
        }
    }

    @NotNull
    private BiConsumer<D, Throwable> whenComplete(CompletionStage<D> completionStage) {
        return (d, throwable) -> {
            if (isTerminal()) {
                return;
            }
            whenNextFinished(completionStage, d, throwable);
        };
    }

    /**
     * This is called as each mapped {@link CompletionStage} completes with
     * a value or exception
     *
     * @param completionStage the completion stage that has completed
     * @param d               the value completed
     * @param throwable       or the throwable that happened during completion
     */
    protected void whenNextFinished(CompletionStage<D> completionStage, D d, Throwable throwable) {
        try {
            if (throwable != null) {
                handleThrowableDuringMapping(throwable);
            } else {
                downstreamSubscriber.onNext(d);
            }
        } finally {
            boolean empty = removeFromInFlightQAndCheckIfEmpty(completionStage);
            finallyAfterEachPromiseFinishes(empty);
        }
    }

    protected void finallyAfterEachPromiseFinishes(boolean isInFlightEmpty) {
        //
        // if the runOnCompleteOrErrorRun runnable is set, the upstream has
        // called onComplete() already, but the CFs have not all completed
        // yet, so we have to check whenever a CF completes
        //
        Runnable runOnCompleteOrErrorRun = onCompleteRun.get();
        if (isInFlightEmpty && runOnCompleteOrErrorRun != null) {
            onCompleteRun.set(null);
            runOnCompleteOrErrorRun.run();
        }
    }

    protected void handleThrowableDuringMapping(Throwable throwable) {
        // only do this once
        if (isTerminal.compareAndSet(false, true)) {
            downstreamSubscriber.onError(throwable);
            //
            // Reactive semantics say that IF an exception happens on a publisher,
            // then onError is called and no more messages flow.  But since the exception happened
            // during the mapping, the upstream publisher does not know about this.
            // So we cancel to bring the semantics back together, that is as soon as an exception
            // has happened, no more messages flow
            //
            delegatingSubscription.cancel();

            cancelInFlightFutures();
        }
    }

    @Override
    public void onError(Throwable t) {
        // we immediately terminate - we don't wait for any promises to complete
        if (isTerminal.compareAndSet(false, true)) {
            downstreamSubscriber.onError(t);
            cancelInFlightFutures();
        }
    }

    @Override
    public void onComplete() {
        onComplete(() -> {
            if (isTerminal.compareAndSet(false, true)) {
                downstreamSubscriber.onComplete();
            }
        });
    }

    private void onComplete(Runnable doneCodeToRun) {
        if (inFlightQIsEmpty()) {
            // run right now
            doneCodeToRun.run();
        } else {
            onCompleteRun.set(doneCodeToRun);
        }
    }

    protected void offerToInFlightQ(CompletionStage<?> completionStage) {
        lock.runLocked(() ->
                inFlightDataQ.offer(completionStage)
        );
    }

    private boolean removeFromInFlightQAndCheckIfEmpty(CompletionStage<?> completionStage) {
        // uncontested locks in java are cheap - we don't expect much contention here
        return lock.callLocked(() -> {
            inFlightDataQ.remove(completionStage);
            return inFlightDataQ.isEmpty();
        });
    }

    /**
     * If the promise is backed by frameworks such as Reactor, then the cancel()
     * can cause them to propagate the cancel back into the reactive chain
     */
    private void cancelInFlightFutures() {
        lock.runLocked(() -> {
            while (!inFlightDataQ.isEmpty()) {
                CompletionStage<?> cs = inFlightDataQ.poll();
                if (cs != null) {
                    cs.toCompletableFuture().cancel(false);
                }
            }
        });
    }

    protected boolean inFlightQIsEmpty() {
        return lock.callLocked(inFlightDataQ::isEmpty);
    }

    /**
     * The two terminal states are onComplete or onError
     *
     * @return true if it's in a terminal state
     */
    protected boolean isTerminal() {
        return isTerminal.get();
    }
}
