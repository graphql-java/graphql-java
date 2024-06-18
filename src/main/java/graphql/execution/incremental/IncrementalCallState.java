package graphql.execution.incremental;

import graphql.Internal;
import graphql.execution.reactive.SingleSubscriberPublisher;
import graphql.incremental.DelayedIncrementalPartialResult;
import graphql.incremental.IncrementalPayload;
import graphql.util.InterThreadMemoizedSupplier;
import graphql.util.LockKit;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static graphql.incremental.DelayedIncrementalPartialResultImpl.newIncrementalExecutionResult;

/**
 * This provides support for @defer directives on fields that mean that results will be sent AFTER
 * the main result is sent via a Publisher stream.
 */
@Internal
public class IncrementalCallState {
    private final AtomicBoolean incrementalCallsDetected = new AtomicBoolean(false);
    private final Deque<IncrementalCall<? extends IncrementalPayload>> incrementalCalls = new ConcurrentLinkedDeque<>();
    private final Supplier<SingleSubscriberPublisher<DelayedIncrementalPartialResult>> publisher = createPublisher();
    private final AtomicInteger pendingCalls = new AtomicInteger();
    private final LockKit.ReentrantLock publisherLock = new LockKit.ReentrantLock();

    @SuppressWarnings("FutureReturnValueIgnored")
    private void drainIncrementalCalls() {
        IncrementalCall<? extends IncrementalPayload> incrementalCall = incrementalCalls.poll();

        while (incrementalCall != null) {
            incrementalCall.invoke()
                    .whenComplete((payload, exception) -> {
                        if (exception != null) {
                            publisher.get().offerError(exception);
                            return;
                        }

                        // The assigment of `remainingCalls` and `publisher.offer` need to be synchronized to ensure
                        // `hasNext` is `false` precisely on the last event offered to the publisher.
                        publisherLock.lock();
                        final int remainingCalls;

                        try {
                            remainingCalls = pendingCalls.decrementAndGet();

                            DelayedIncrementalPartialResult executionResult = newIncrementalExecutionResult()
                                    .incrementalItems(Collections.singletonList(payload))
                                    .hasNext(remainingCalls != 0)
                                    .build();

                            publisher.get().offer(executionResult);
                        } finally {
                            publisherLock.unlock();
                        }

                        if (remainingCalls == 0) {
                            publisher.get().noMoreData();
                        } else {
                            // Nested calls were added, let's try to drain the queue again.
                            drainIncrementalCalls();
                        }
                    });
            incrementalCall = incrementalCalls.poll();
        }
    }

    public void enqueue(IncrementalCall<? extends IncrementalPayload> incrementalCall) {
        publisherLock.runLocked(() -> {
            incrementalCallsDetected.set(true);
            incrementalCalls.offer(incrementalCall);
            pendingCalls.incrementAndGet();
        });
    }

    public void enqueue(Collection<IncrementalCall<? extends IncrementalPayload>> calls) {
        calls.forEach(this::enqueue);
    }

    public boolean getIncrementalCallsDetected() {
        return incrementalCallsDetected.get();
    }

    private Supplier<SingleSubscriberPublisher<DelayedIncrementalPartialResult>> createPublisher() {
        // this will be created once and once only - any extra calls to .get() will return the previously created
        // singleton object
        return new InterThreadMemoizedSupplier<>(() -> new SingleSubscriberPublisher<>(this::drainIncrementalCalls));
    }

    /**
     * This method will return a {@link Publisher} of deferred results.  No field processing will be done
     * until a {@link org.reactivestreams.Subscriber} is attached to this publisher.  Once a {@link org.reactivestreams.Subscriber}
     * is attached the deferred field result processing will be started and published as a series of events.
     *
     * @return the publisher of deferred results
     */
    public Publisher<DelayedIncrementalPartialResult> startDeferredCalls() {
        return publisher.get();
    }
}
