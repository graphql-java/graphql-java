package graphql.execution.reactive;

import graphql.Internal;
import graphql.util.LockKit;
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
    protected final AtomicReference<Runnable> onCompleteOrErrorRun;
    protected final AtomicBoolean isTerminated;

    public CompletionStageSubscriber(Function<U, CompletionStage<D>> mapper, Subscriber<? super D> downstreamSubscriber) {
        this.mapper = mapper;
        this.downstreamSubscriber = downstreamSubscriber;
        inFlightDataQ = new ArrayDeque<>();
        onCompleteOrErrorRun = new AtomicReference<>();
        isTerminated = new AtomicBoolean(false);
    }


    @Override
    public void onSubscribe(Subscription subscription) {
        delegatingSubscription = new DelegatingSubscription(subscription);
        downstreamSubscriber.onSubscribe(delegatingSubscription);
    }

    @Override
    public void onNext(U u) {
        // for safety - no more data after we have called done/error - we should not get this BUT belts and braces
        if (isTerminated()) {
            return;
        }
        try {
            CompletionStage<D> completionStage = mapper.apply(u);
            offerToInFlightQ(completionStage);
            completionStage.whenComplete(whenNextFinished(completionStage));
        } catch (RuntimeException throwable) {
            handleThrowable(throwable);
        }
    }

    /**
     * This is called as each mapped {@link CompletionStage} completes with
     * a value or exception
     *
     * @param completionStage the completion stage that has completed
     *
     * @return a handle function for {@link CompletionStage#whenComplete(BiConsumer)}
     */
    protected BiConsumer<D, Throwable> whenNextFinished(CompletionStage<D> completionStage) {
        return (d, throwable) -> {
            try {
                if (throwable != null) {
                    handleThrowable(throwable);
                } else {
                    downstreamSubscriber.onNext(d);
                }
            } finally {
                boolean empty = removeFromInFlightQAndCheckIfEmpty(completionStage);
                finallyAfterEachPromisesFinishes(empty);
            }
        };
    }

    protected void finallyAfterEachPromisesFinishes(boolean isInFlightEmpty) {
        //
        // if the runOnCompleteOrErrorRun runnable is set, the upstream has
        // called onError() or onComplete() already, but the CFs have not all completed
        // yet, so we have to check whenever a CF completes
        //
        Runnable runOnCompleteOrErrorRun = onCompleteOrErrorRun.get();
        if (isInFlightEmpty && runOnCompleteOrErrorRun != null) {
            onCompleteOrErrorRun.set(null);
            runOnCompleteOrErrorRun.run();
        }
    }

    protected void handleThrowable(Throwable throwable) {
        // only do this once
        if (isTerminated.compareAndSet(false, true)) {
            downstreamSubscriber.onError(throwable);
            //
            // Reactive semantics say that IF an exception happens on a publisher,
            // then onError is called and no more messages flow.  But since the exception happened
            // during the mapping, the upstream publisher does not know about this.
            // So we cancel to bring the semantics back together, that is as soon as an exception
            // has happened, no more messages flow
            //
            delegatingSubscription.cancel();
        }
    }

    @Override
    public void onError(Throwable t) {
        onCompleteOrError(() -> {
            isTerminated.set(true);
            downstreamSubscriber.onError(t);
        });
    }

    @Override
    public void onComplete() {
        onCompleteOrError(() -> {
            isTerminated.set(true);
            downstreamSubscriber.onComplete();
        });
    }

    /**
     * Get instance of downstream subscriber
     *
     * @return {@link Subscriber}
     */
    public Subscriber<? super D> getDownstreamSubscriber() {
        return downstreamSubscriber;
    }

    private void onCompleteOrError(Runnable doneCodeToRun) {
        if (inFlightQIsEmpty()) {
            // run right now
            doneCodeToRun.run();
        } else {
            onCompleteOrErrorRun.set(doneCodeToRun);
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

    protected boolean inFlightQIsEmpty() {
        return lock.callLocked(inFlightDataQ::isEmpty);
    }

    protected boolean isTerminated() {
        return isTerminated.get();
    }
}
