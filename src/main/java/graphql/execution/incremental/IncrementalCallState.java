package graphql.execution.incremental;

import graphql.Internal;
import graphql.execution.reactive.SingleSubscriberPublisher;
import graphql.incremental.DelayedIncrementalPartialResult;
import graphql.incremental.IncrementalPayload;
import graphql.util.InterThreadMemoizedSupplier;
import graphql.util.LockKit;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final Set<AlternativeCallContext> activeParents = new LinkedHashSet<>();
    private final Map<IncrementalCall<? extends IncrementalPayload>, AlternativeCallContext> callParents = new LinkedHashMap<>();
    private final Map<AlternativeCallContext, List<IncrementalCall<? extends IncrementalPayload>>> readyChildrenByParent = new LinkedHashMap<>();
    private final Map<IncrementalCall<? extends IncrementalPayload>, IncrementalPayload> readyPayloads = new LinkedHashMap<>();

    @SuppressWarnings("FutureReturnValueIgnored")
    private void drainIncrementalCalls() {
        IncrementalCall<? extends IncrementalPayload> incrementalCall = incrementalCalls.poll();

        while (incrementalCall != null) {
            IncrementalCall<? extends IncrementalPayload> currentCall = incrementalCall;
            currentCall.invoke()
                    .whenComplete((payload, exception) -> {
                        if (exception != null) {
                            publisher.get().offerError(exception);
                            return;
                        }

                        // The assignment of `remainingCalls` and `publisher.offer` need to be synchronized to ensure
                        // `hasNext` is `false` precisely on the last event offered to the publisher, and that parent
                        // deferred fragments are always offered before their nested children.
                        final int remainingCalls = publisherLock.callLocked(() -> onCallCompleted(currentCall, payload));

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

    private int onCallCompleted(IncrementalCall<? extends IncrementalPayload> completedCall, IncrementalPayload payload) {
        AlternativeCallContext parentContext = callParents.remove(completedCall);
        if (parentContext != null && activeParents.contains(parentContext)) {
            readyChildrenByParent.computeIfAbsent(parentContext, key -> new ArrayList<>()).add(completedCall);
            readyPayloads.put(completedCall, payload);
            return pendingCalls.get();
        }

        readyPayloads.put(completedCall, payload);
        Deque<IncrementalCall<? extends IncrementalPayload>> toEmit = new ArrayDeque<>();
        toEmit.add(completedCall);

        int remainingCalls = pendingCalls.get();
        while (!toEmit.isEmpty()) {
            IncrementalCall<? extends IncrementalPayload> nextCall = toEmit.poll();
            IncrementalPayload nextPayload = Objects.requireNonNull(readyPayloads.remove(nextCall));
            remainingCalls = publishPayload(nextPayload);
            releaseChildren(nextCall, toEmit);
        }
        return remainingCalls;
    }

    private int publishPayload(IncrementalPayload payload) {
        int remainingCalls = pendingCalls.decrementAndGet();
        DelayedIncrementalPartialResult executionResult = newIncrementalExecutionResult()
                .incrementalItems(Collections.singletonList(payload))
                .hasNext(remainingCalls != 0)
                .build();
        publisher.get().offer(executionResult);
        return remainingCalls;
    }

    private void releaseChildren(
            IncrementalCall<? extends IncrementalPayload> emittedCall,
            Deque<IncrementalCall<? extends IncrementalPayload>> toEmit
    ) {
        if (!(emittedCall instanceof DeferredFragmentCall)) {
            return;
        }
        AlternativeCallContext ownContext = ((DeferredFragmentCall) emittedCall).getAlternativeCallContext();
        activeParents.remove(ownContext);
        List<IncrementalCall<? extends IncrementalPayload>> readyChildren = readyChildrenByParent.remove(ownContext);
        if (readyChildren != null) {
            toEmit.addAll(readyChildren);
        }
    }

    public void enqueue(IncrementalCall<? extends IncrementalPayload> incrementalCall) {
        enqueue(incrementalCall, null);
    }

    public void enqueue(
            IncrementalCall<? extends IncrementalPayload> incrementalCall,
            @Nullable AlternativeCallContext parentContext
    ) {
        publisherLock.runLocked(() -> {
            if (incrementalCall instanceof DeferredFragmentCall) {
                activeParents.add(((DeferredFragmentCall) incrementalCall).getAlternativeCallContext());
            }
            if (parentContext != null && activeParents.contains(parentContext)) {
                callParents.put(incrementalCall, parentContext);
            }
            incrementalCallsDetected.set(true);
            incrementalCalls.offer(incrementalCall);
            pendingCalls.incrementAndGet();
        });
    }

    public void enqueue(Collection<IncrementalCall<? extends IncrementalPayload>> calls) {
        enqueue(calls, null);
    }

    public void enqueue(
            Collection<IncrementalCall<? extends IncrementalPayload>> calls,
            @Nullable AlternativeCallContext parentContext
    ) {
        calls.forEach(call -> enqueue(call, parentContext));
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

    public void startDrainingNow() {
        drainIncrementalCalls();
    }

}
