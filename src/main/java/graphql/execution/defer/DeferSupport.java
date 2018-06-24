package graphql.execution.defer;

import graphql.Directives;
import graphql.ExecutionResult;
import graphql.Internal;
import graphql.execution.reactive.SingleSubscriberPublisher;
import graphql.language.Field;
import org.reactivestreams.Publisher;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This provides support for @defer directives on fields that mean that results will be sent AFTER
 * the main result is sent via a Publisher stream.
 */
@Internal
public class DeferSupport {

    private final AtomicBoolean deferDetected = new AtomicBoolean(false);
    private final Deque<DeferredCall> deferredCalls = new ConcurrentLinkedDeque<>();
    private final SingleSubscriberPublisher<ExecutionResult> publisher = new SingleSubscriberPublisher<>();

    public boolean checkForDeferDirective(List<Field> currentField) {
        for (Field field : currentField) {
            if (field.getDirective(Directives.DeferDirective.getName()) != null) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private void drainDeferredCalls() {
        if (deferredCalls.isEmpty()) {
            publisher.noMoreData();
            return;
        }
        DeferredCall deferredCall = deferredCalls.pop();
        CompletableFuture<ExecutionResult> future = deferredCall.invoke();
        future.whenComplete((executionResult, exception) -> {
            if (exception != null) {
                publisher.offerError(exception);
                return;
            }
            publisher.offer(executionResult);
            drainDeferredCalls();
        });
    }

    public void enqueue(DeferredCall deferredCall) {
        deferDetected.set(true);
        deferredCalls.offer(deferredCall);
    }

    public boolean isDeferDetected() {
        return deferDetected.get();
    }

    /**
     * When this is called the deferred execution will begin
     *
     * @return the publisher of deferred results
     */
    public Publisher<ExecutionResult> startDeferredCalls() {
        drainDeferredCalls();
        return publisher;
    }
}
