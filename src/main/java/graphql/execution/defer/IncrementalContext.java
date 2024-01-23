package graphql.execution.defer;

import graphql.Internal;
import graphql.execution.reactive.SingleSubscriberPublisher;
import graphql.incremental.DelayedIncrementalExecutionResult;
import graphql.incremental.IncrementalPayload;
import graphql.util.LockKit;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.incremental.DelayedIncrementalExecutionResultImpl.newIncrementalExecutionResult;

/**
 * This provides support for @defer directives on fields that mean that results will be sent AFTER
 * the main result is sent via a Publisher stream.
 */
@Internal
public class IncrementalContext {
    private final AtomicBoolean deferDetected = new AtomicBoolean(false);
    private final Deque<IncrementalCall<? extends IncrementalPayload>> incrementalCalls = new ConcurrentLinkedDeque<>();
    private final SingleSubscriberPublisher<DelayedIncrementalExecutionResult> publisher = new SingleSubscriberPublisher<>();
    private final AtomicInteger pendingCalls = new AtomicInteger();
    private final LockKit.ReentrantLock publisherLock = new LockKit.ReentrantLock();

    @SuppressWarnings("FutureReturnValueIgnored")
    private void drainDeferredCalls() {
        IncrementalCall<? extends IncrementalPayload> incrementalCall = incrementalCalls.poll();

        while (incrementalCall != null) {
            incrementalCall.invoke()
                    .whenComplete((payload, exception) -> {
                        if (exception != null) {
                            publisher.offerError(exception);
                            return;
                        }

                        // The assigment of `remainingCalls` and `publisher.offer` need to be synchronized to ensure
                        // `hasNext` is `false` precisely on the last event offered to the publisher.
                        publisherLock.lock();
                        final int remainingCalls;

                        try {
                            remainingCalls = pendingCalls.decrementAndGet();

                            DelayedIncrementalExecutionResult executionResult = newIncrementalExecutionResult()
                                    .incrementalItems(Collections.singletonList(payload))
                                    .hasNext(remainingCalls != 0)
                                    .build();

                            publisher.offer(executionResult);
                        } finally {
                            publisherLock.unlock();
                        }

                        if (remainingCalls == 0) {
                            publisher.noMoreData();
                        } else {
                            // Nested calls were added, let's try to drain the queue again.
                            drainDeferredCalls();
                        }
                    });
            incrementalCall = incrementalCalls.poll();
        }
    }

    public void enqueue(DeferredCall deferredCall) {
        deferDetected.set(true);
        incrementalCalls.offer(deferredCall);
        pendingCalls.incrementAndGet();
    }

    public void enqueue(Collection<DeferredCall> calls) {
        if (!calls.isEmpty()) {
            deferDetected.set(true);
            incrementalCalls.addAll(calls);
            pendingCalls.addAndGet(calls.size());
        }
    }

    public boolean isDeferDetected() {
        return deferDetected.get();
    }

    /**
     * When this is called the deferred execution will begin
     *
     * @return the publisher of deferred results
     */
    public Publisher<DelayedIncrementalExecutionResult> startDeferredCalls() {
        drainDeferredCalls();
        return publisher;
    }
}
