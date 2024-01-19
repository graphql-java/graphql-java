package graphql.execution.defer;

import graphql.Internal;
import graphql.execution.ExecutionContext;
import graphql.execution.MergedField;
import graphql.execution.ValuesResolver;
import graphql.execution.reactive.SingleSubscriberPublisher;
import graphql.incremental.DelayedIncrementalExecutionResult;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.util.LockKit;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static graphql.Directives.DeferDirective;
import static graphql.incremental.DelayedIncrementalExecutionResultImpl.newIncrementalExecutionResult;

/**
 * This provides support for @defer directives on fields that mean that results will be sent AFTER
 * the main result is sent via a Publisher stream.
 */
@Internal
// TODO: This should be called IncrementalSupport and handle both @defer and @stream
public class DeferExecutionSupport {
    private final AtomicBoolean deferDetected = new AtomicBoolean(false);
    private final Deque<DeferredCall> deferredCalls = new ConcurrentLinkedDeque<>();
    private final SingleSubscriberPublisher<DelayedIncrementalExecutionResult> publisher = new SingleSubscriberPublisher<>();
    private final AtomicInteger pendingCalls = new AtomicInteger();
    private final LockKit.ReentrantLock publisherLock = new LockKit.ReentrantLock();


    public boolean checkForDeferDirective(MergedField currentField, ExecutionContext executionContext) {
        for (Field field : currentField.getFields()) {
            List<Directive> directives = field.getDirectives(DeferDirective.getName());
            // TODO: How to best deal with repeated directives here - @defer/@stream is not a repeated directive
            Directive directive = directives.stream().findFirst().orElse(null);
            if (directive != null) {
                Map<String, Object> argumentValues = ValuesResolver.getArgumentValues(
                        DeferDirective.getArguments(),
                        directive.getArguments(),
                        executionContext.getCoercedVariables(),
                        executionContext.getGraphQLContext(),
                        executionContext.getLocale()
                );
                return (Boolean) argumentValues.get("if");
            }
        }
        return false;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void drainDeferredCalls() {
        DeferredCall deferredCall = deferredCalls.poll();

        while (deferredCall != null) {
            deferredCall.invoke()
                    .whenComplete((payload, exception) -> {
                        if (exception != null) {
                            publisher.offerError(exception);
                            return;
                        }

                        publisherLock.lock();
                        final int remainingCalls;

                        try {
                            // The assigment of `remainingCalls` and `publisher.offer` need to be synchronized to ensure
                            // `hasNext` is `false` precisely on the last event offered to the publisher.
                            remainingCalls = pendingCalls.decrementAndGet();

                            DelayedIncrementalExecutionResult executionResult = newIncrementalExecutionResult()
                                    .incrementalItems(Collections.singletonList(payload))
                                    .hasNext(remainingCalls != 0)
                                    .build();

                            publisher.offer(executionResult);
                        } finally {
                            publisherLock.unlock();
                        }
                        ;

                        if (remainingCalls == 0) {
                            publisher.noMoreData();
                        } else {
                            // Nested calls were added, let's try to drain the queue again.
                            drainDeferredCalls();
                        }
                    });
            deferredCall = deferredCalls.poll();
        }
    }

    public void enqueue(DeferredCall deferredCall) {
        deferDetected.set(true);
        deferredCalls.offer(deferredCall);
        pendingCalls.incrementAndGet();
    }

    public void enqueue(Collection<DeferredCall> calls) {
        if (!calls.isEmpty()) {
            deferDetected.set(true);
            deferredCalls.addAll(calls);
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
