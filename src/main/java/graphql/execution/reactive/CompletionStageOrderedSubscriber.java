package graphql.execution.reactive;

import graphql.Internal;
import org.reactivestreams.Subscriber;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * This subscriber can be used to map between a {@link org.reactivestreams.Publisher} of U
 * elements and map them into {@link CompletionStage} of D promises, and it keeps them in the order
 * the Publisher provided them.
 *
 * @param <U> published upstream elements
 * @param <D> mapped downstream values
 */
@Internal
public class CompletionStageOrderedSubscriber<U, D> extends CompletionStageSubscriber<U, D> implements Subscriber<U> {

    public CompletionStageOrderedSubscriber(Function<U, CompletionStage<D>> mapper, Subscriber<? super D> downstreamSubscriber) {
        super(mapper, downstreamSubscriber);
    }

    @Override
    protected void whenNextFinished(CompletionStage<D> completionStage, D d, Throwable throwable) {
        try {
            if (throwable != null) {
                handleThrowableDuringMapping(throwable);
            } else {
                emptyInFlightQueueIfWeCan();
            }
        } finally {
            boolean empty = inFlightQIsEmpty();
            finallyAfterEachPromiseFinishes(empty);
        }
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
                            handleThrowableDuringMapping(cfExceptionUnwrap(rte));
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

    private Throwable cfExceptionUnwrap(Throwable throwable) {
        if (throwable instanceof CompletionException & throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
