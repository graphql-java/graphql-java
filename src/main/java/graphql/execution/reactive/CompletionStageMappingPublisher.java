package graphql.execution.reactive;

import graphql.Internal;
import graphql.util.LockKit;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static graphql.Assert.assertNotNullWithNPE;

/**
 * A reactive Publisher that bridges over another Publisher of `D` and maps the results
 * to type `U` via a CompletionStage, handling errors in that stage
 *
 * @param <D> the down stream type
 * @param <U> the up stream type to be mapped to
 */
@SuppressWarnings("ReactiveStreamsPublisherImplementation")
@Internal
public class CompletionStageMappingPublisher<D, U> implements Publisher<D> {
    private final Publisher<U> upstreamPublisher;
    private final Function<U, CompletionStage<D>> mapper;

    /**
     * You need the following :
     *
     * @param upstreamPublisher an upstream source of data
     * @param mapper            a mapper function that turns upstream data into a promise of mapped D downstream data
     */
    public CompletionStageMappingPublisher(Publisher<U> upstreamPublisher, Function<U, CompletionStage<D>> mapper) {
        this.upstreamPublisher = upstreamPublisher;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Subscriber<? super D> downstreamSubscriber) {
        assertNotNullWithNPE(downstreamSubscriber, () -> "Subscriber passed to subscribe must not be null");
        upstreamPublisher.subscribe(new CompletionStageSubscriber(downstreamSubscriber));
    }

    /**
     * Get instance of an upstreamPublisher
     *
     * @return upstream instance of {@link Publisher}
     */
    public Publisher<U> getUpstreamPublisher() {
        return upstreamPublisher;
    }

    @SuppressWarnings("ReactiveStreamsSubscriberImplementation")
    @Internal
    public class CompletionStageSubscriber implements Subscriber<U> {
        private final Subscriber<? super D> downstreamSubscriber;
        Subscription delegatingSubscription;
        final Queue<CompletionStage<?>> inFlightDataQ;
        final LockKit.ReentrantLock lock = new LockKit.ReentrantLock();
        final AtomicReference<Runnable> onCompleteOrErrorRun;
        final AtomicBoolean onCompleteOrErrorRunCalled;
        final AtomicBoolean upstreamCancelled;

        public CompletionStageSubscriber(Subscriber<? super D> downstreamSubscriber) {
            this.downstreamSubscriber = downstreamSubscriber;
            inFlightDataQ = new ArrayDeque<>();
            onCompleteOrErrorRun = new AtomicReference<>();
            onCompleteOrErrorRunCalled = new AtomicBoolean(false);
            upstreamCancelled = new AtomicBoolean(false);
        }


        @Override
        public void onSubscribe(Subscription subscription) {
            delegatingSubscription = new DelegatingSubscription(subscription);
            downstreamSubscriber.onSubscribe(delegatingSubscription);
        }

        @Override
        public void onNext(U u) {
            // for safety - no more data after we have called done/error - we should not get this BUT belts and braces
            if (onCompleteOrErrorRunCalled.get()) {
                return;
            }
            try {
                CompletionStage<D> completionStage = mapper.apply(u);
                offerToInFlightQ(completionStage);
                completionStage.whenComplete(whenNextFinished());
            } catch (RuntimeException throwable) {
                handleThrowable(throwable);
            }
        }

        private BiConsumer<D, Throwable> whenNextFinished() {
            return (d, throwable) -> {
                try {
                    if (throwable != null) {
                        handleThrowable(throwable);
                    } else {
                        emptyInFlightQueueIfWeCan();
                    }
                } finally {
                    Runnable runOnCompleteOrErrorRun = onCompleteOrErrorRun.get();
                    boolean empty = inFlightQIsEmpty();
                    //
                    // if the runOnCompleteOrErrorRun runnable is set, the upstream has
                    // called onError() or onComplete() already, but the CFs have not all completed
                    // yet, so we have to check whenever a CF completes
                    //
                    if (empty && runOnCompleteOrErrorRun != null) {
                        onCompleteOrErrorRun.set(null);
                        runOnCompleteOrErrorRun.run();
                    }
                }
            };
        }

        private void emptyInFlightQueueIfWeCan() {
            // done inside a memory lock, so we cant offer new CFs to the queue
            // until we have processed any completed ones from the start of
            // the queue.
            lock.runLocked(() -> {
                //
                // from the top of the in flight queue, take all the CFs that have
                // completed... but stop if they are not done
                while (!inFlightDataQ.isEmpty()) {
                    CompletionStage<?> cs = inFlightDataQ.peek();
                    if (cs != null) {
                        //
                        CompletableFuture<?> cf = cs.toCompletableFuture();
                        if (cf.isDone()) {
                            // take it off the queue
                            inFlightDataQ.poll();
                            D value;
                            try {
                                //noinspection unchecked
                                value = (D) cf.join();
                            } catch (RuntimeException rte) {
                                //
                                // if we get an exception while joining on a value, we
                                // send it into the exception handling and break out
                                handleThrowable(cfExceptionUnwrap(rte));
                                break;
                            }
                            downstreamSubscriber.onNext(value);
                        } else {
                            // if the CF is not done, then we have to stop processing
                            // to keep the results in order inside the inFlightQueue
                            break;
                        }
                    }
                }
            });
        }

        private void handleThrowable(Throwable throwable) {
            // only do this once
            if (upstreamCancelled.compareAndSet(false,true)) {
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
                onCompleteOrErrorRunCalled.set(true);
                downstreamSubscriber.onError(t);
            });
        }

        @Override
        public void onComplete() {
            onCompleteOrError(() -> {
                onCompleteOrErrorRunCalled.set(true);
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

        private void offerToInFlightQ(CompletionStage<?> completionStage) {
            lock.runLocked(() ->
                    inFlightDataQ.offer(completionStage)
            );
        }

        private boolean inFlightQIsEmpty() {
            return lock.callLocked(inFlightDataQ::isEmpty);
        }

        private Throwable cfExceptionUnwrap(Throwable throwable) {
            if (throwable instanceof CompletionException  & throwable.getCause() != null) {
                return throwable.getCause();
            }
            return throwable;
        }
    }
}
