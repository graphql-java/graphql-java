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
        boolean ok = drainDeferredCalls(subscriber);
        if (ok) {
            subscriber.onComplete();
        }
    }

    private boolean drainDeferredCalls(Subscriber<? super ExecutionResult> subscriber) {
        while (!deferredCalls.isEmpty()) {
            Supplier<CompletableFuture<ExecutionResult>> deferredExecution = deferredCalls.pop();
            try {
                CompletableFuture<ExecutionResult> future = deferredExecution.get();
                //
                // this becomes a blocking call on the deferred resolveField value.  This and the use of the queue
                // means we will resolve them in "encountered" order.  We could do it async and hence in any order
                //
                ExecutionResult executionResult = future.get();
                executionResult = addAnyErrorsEncountered(executionResult);
                subscriber.onNext(executionResult);
            } catch (Exception e) {
                subscriber.onError(e);
                return false;
            }
        }
        return true;
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
