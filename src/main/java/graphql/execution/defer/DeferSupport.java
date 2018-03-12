package graphql.execution.defer;

import graphql.Directives;
import graphql.ExecutionResult;
import graphql.GraphQLException;
import graphql.Internal;
import graphql.execution.reactive.CancellableSubscription;
import graphql.language.Field;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This provides support for @defer directives on fields that mean that results will be sent AFTER
 * the main result is sent via a Publisher stream.
 */
@Internal
public class DeferSupport implements Publisher<ExecutionResult> {

    public static final String DEFERRED_RESULT_STREAM_NAME = "deferredResultStream";

    private final Deque<DeferredCall> deferredCalls = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean deferDetected = new AtomicBoolean(false);
    private final AtomicReference<CancellableSubscription> subscription = new AtomicReference<>();

    public boolean checkForDeferDirective(List<Field> currentField) {
        for (Field field : currentField) {
            if (field.getDirective(Directives.DeferDirective.getName()) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void subscribe(Subscriber<? super ExecutionResult> subscriber) {
        if (subscription.getAndSet(new CancellableSubscription()) != null) {
            throw new GraphQLException("The @defer code only supports one subscription to the results");
        }
        subscriber.onSubscribe(subscription.get());
        drainDeferredCalls(subscriber);
    }

    private void drainDeferredCalls(Subscriber<? super ExecutionResult> subscriber) {
        if (deferredCalls.isEmpty()) {
            subscriber.onComplete();
        }
        if (isCancelled()) {
            subscriber.onComplete();
            return;
        }
        DeferredCall deferredCall = deferredCalls.pop();
        CompletableFuture<ExecutionResult> future = deferredCall.invoke();
        future.whenComplete((executionResult, exception) -> {
            if (isCancelled()) {
                subscriber.onComplete();
                return;
            }
            if (exception != null) {
                subscriber.onError(exception);
                return;
            }
            subscriber.onNext(executionResult);
            drainDeferredCalls(subscriber);
        });
    }

    private boolean isCancelled() {
        return subscription.get().isCancelled();
    }

    public void enqueue(DeferredCall deferredCall) {
        deferDetected.set(true);
        deferredCalls.offer(deferredCall);
    }

    public boolean isDeferDetected() {
        return deferDetected.get();
    }

    public Publisher<ExecutionResult> getPublisher() {
        return this;
    }
}
