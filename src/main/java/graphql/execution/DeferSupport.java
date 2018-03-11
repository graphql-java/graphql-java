package graphql.execution;

import graphql.Directives;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.language.Field;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * This provides support for @defer directives on fields that mean that results will be sent AFTER
 * the main result is sent via a Publisher stream.
 */
@Internal
public class DeferSupport implements Publisher<ExecutionResult> {

    private final Deque<Supplier<CompletableFuture<ExecutionResult>>> deferredCalls = new ConcurrentLinkedDeque<>();
    private final List<GraphQLError> errorsEncountered = new ArrayList<>();
    private final AtomicBoolean deferDetected = new AtomicBoolean(false);

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
        subscriber.onSubscribe(emptySubscription());
        drainDeferredCalls(subscriber);
    }

    private void drainDeferredCalls(Subscriber<? super ExecutionResult> subscriber) {
        if (deferredCalls.isEmpty()) {
            subscriber.onComplete();
        }
        Supplier<CompletableFuture<ExecutionResult>> deferredExecutionSupplier = deferredCalls.pop();
        CompletableFuture<ExecutionResult> future = deferredExecutionSupplier.get();
        future.whenComplete((executionResult, exception) -> {
            if (exception != null) {
                subscriber.onError(exception);
                return;
            }
            executionResult = addAnyErrorsEncountered(executionResult);
            subscriber.onNext(executionResult);
            drainDeferredCalls(subscriber);
        });
    }

    private ExecutionResult addAnyErrorsEncountered(ExecutionResult executionResult) {
        synchronized (errorsEncountered) {
            ExecutionResultImpl sourceResult = (ExecutionResultImpl) executionResult;
            ExecutionResultImpl.Builder builder = ExecutionResultImpl.newExecutionResult().from(sourceResult);

            builder.addErrors(errorsEncountered);
            errorsEncountered.clear();

            return builder.build();
        }
    }


    public void onFetcherError(GraphQLError error) {
        synchronized (errorsEncountered) {
            errorsEncountered.add(error);
        }
    }

    public void enqueue(Supplier<CompletableFuture<ExecutionResult>> deferredCall) {
        deferDetected.set(true);
        deferredCalls.offer(deferredCall);
    }

    public boolean isDeferDetected() {
        return deferDetected.get();
    }

    public Publisher<ExecutionResult> getPublisher() {
        return this;
    }

    private Subscription emptySubscription() {
        return new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        };
    }
}
