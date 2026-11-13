package graphql.execution.reactive

import graphql.execution.pubsub.CapturingSubscriber
import graphql.execution.pubsub.CapturingSubscription
import org.reactivestreams.Subscriber

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

class CompletionStageOrderedSubscriberTest extends CompletionStageSubscriberTest {

    @Override
    protected Subscriber<Integer> createSubscriber(Function<Integer, CompletionStage<String>> mapper, CapturingSubscriber<Object> capturingSubscriber) {
        return new CompletionStageOrderedSubscriber<Integer, String>(mapper, capturingSubscriber)
    }

    @Override
    protected ArrayList<String> expectedOrderingOfAsyncCompletion() {
        return ["0", "1", "2", "3"]
    }

    /**
     * A CompletableFuture that is already completed exceptionally but suppresses whenComplete
     * callbacks. This simulates the race condition where a CF fails but its whenComplete callback
     * has not yet run, so another CF's callback discovers the failure during queue drain via cf.join().
     */
    static class SilentlyFailedFuture<T> extends CompletableFuture<T> {
        SilentlyFailedFuture(Throwable ex) {
            super.completeExceptionally(ex)
        }

        @Override
        CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            // Return a new never-completing future instead of registering the callback.
            // This simulates the race: the CF is done but its callback hasn't fired yet.
            return new CompletableFuture<>()
        }
    }

    def "handles exceptionally completed CF discovered during ordered queue drain (cfExceptionUnwrap)"() {
        given: "a subscriber with a mapper that returns a silently-failed CF for item 0 and a normal CF for item 1"
        def capturingSubscriber = new CapturingSubscriber<>()
        def subscription = new CapturingSubscription()

        def originalException = new RuntimeException("boom")
        // Item 0: already failed, but whenComplete callback is suppressed
        def silentlyFailed = new SilentlyFailedFuture<String>(originalException)
        // Item 1: completes normally and immediately
        def normalCf = CompletableFuture.completedFuture("one")

        int callCount = 0
        Function<Integer, CompletionStage<String>> mapper = { Integer i ->
            if (callCount++ == 0) return silentlyFailed
            return normalCf
        }

        def subscriber = new CompletionStageOrderedSubscriber<Integer, String>(mapper, capturingSubscriber)

        when: "we subscribe and send two items"
        subscriber.onSubscribe(subscription)
        // Item 0: silently-failed CF is added to the queue; its whenComplete does nothing
        subscriber.onNext(0)
        // Item 1: normal CF completes immediately; its whenComplete fires and calls
        // emptyInFlightQueueIfWeCan, which drains from the head and hits silentlyFailed.join()
        subscriber.onNext(1)

        then: "the downstream subscriber receives onError with the unwrapped original exception"
        capturingSubscriber.isCompletedExceptionally()
        // cfExceptionUnwrap should unwrap the CompletionException to the original cause
        capturingSubscriber.throwable == originalException
        capturingSubscriber.events == []
    }

    def "handles exceptionally completed CF when exception is not a CompletionException"() {
        given: "a subscriber where item 0 fails with a plain RuntimeException (not CompletionException)"
        def capturingSubscriber = new CapturingSubscriber<>()
        def subscription = new CapturingSubscription()

        // Use completeExceptionally with a CompletionException that has no cause,
        // so cfExceptionUnwrap returns it as-is
        def exceptionWithoutCause = new CompletionException("no cause", null)
        def silentlyFailed = new SilentlyFailedFuture<String>(exceptionWithoutCause)
        def normalCf = CompletableFuture.completedFuture("one")

        int callCount = 0
        Function<Integer, CompletionStage<String>> mapper = { Integer i ->
            if (callCount++ == 0) return silentlyFailed
            return normalCf
        }

        def subscriber = new CompletionStageOrderedSubscriber<Integer, String>(mapper, capturingSubscriber)

        when:
        subscriber.onSubscribe(subscription)
        subscriber.onNext(0)
        subscriber.onNext(1)

        then: "CompletionException without a cause is passed through (wrapped in another CompletionException by join)"
        capturingSubscriber.isCompletedExceptionally()
        // join() wraps in CompletionException; cfExceptionUnwrap unwraps to the original CompletionException
        capturingSubscriber.throwable == exceptionWithoutCause
    }
}
