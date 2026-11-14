package graphql.execution.reactive

import graphql.execution.pubsub.CapturingSubscriber
import graphql.execution.pubsub.CapturingSubscription
import org.awaitility.Awaitility
import org.reactivestreams.Subscriber
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * This can be used to test the CompletionStageSubscriber and CompletionStageOrderedSubscriber
 * since they share so much common code.  There are protected helpers to create the subscribers
 * and set expectations
 */
class CompletionStageSubscriberTest extends Specification {

    protected Subscriber<Integer> createSubscriber(Function<Integer, CompletionStage<String>> mapper, CapturingSubscriber<Object> capturingSubscriber) {
        def completionStageSubscriber = new CompletionStageSubscriber<Integer, String>(mapper, capturingSubscriber)
        completionStageSubscriber
    }

    protected ArrayList<String> expectedOrderingOfAsyncCompletion() {
        ["3", "2", "1", "0"]
    }

    def "basic test of mapping"() {
        def capturingSubscriber = new CapturingSubscriber<>()
        def subscription = new CapturingSubscription()
        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                return CompletableFuture.completedFuture(String.valueOf(integer))
            }
        }
        Subscriber<Integer> completionStageSubscriber = createSubscriber(mapper, capturingSubscriber)

        when:
        completionStageSubscriber.onSubscribe(subscription)
        completionStageSubscriber.onNext(0)

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.events == ["0"]

        when:
        completionStageSubscriber.onNext(1)

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.events == ["0", "1"]

        when:
        completionStageSubscriber.onComplete()

        then:
        !subscription.isCancelled()
        capturingSubscriber.isCompleted()
        capturingSubscriber.events == ["0", "1"]
    }

    def "can hold CFs that have not completed and does not emit them even when onComplete is called"() {
        def capturingSubscriber = new CapturingSubscriber<>()
        def subscription = new CapturingSubscription()
        List<Runnable> promises = []
        Function<Integer, CompletionStage<String>> mapper = mapperThatDoesNotComplete(promises)
        Subscriber<Integer> completionStageSubscriber = createSubscriber(mapper, capturingSubscriber)

        when:
        completionStageSubscriber.onSubscribe(subscription)
        completionStageSubscriber.onNext(0)

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.events == []

        when:
        completionStageSubscriber.onNext(1)

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.events == []

        when:
        completionStageSubscriber.onComplete()

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.events == []

        when:
        promises.forEach { it.run() }

        then:
        !subscription.isCancelled()
        capturingSubscriber.isCompleted()
        capturingSubscriber.events == ["0", "1"]
    }

    def "can hold CFs that have not completed but finishes quickly when onError is called"() {
        def capturingSubscriber = new CapturingSubscriber<>()
        def subscription = new CapturingSubscription()
        List<Runnable> promises = []
        Function<Integer, CompletionStage<String>> mapper = mapperThatDoesNotComplete(promises)

        Subscriber<Integer> completionStageSubscriber = createSubscriber(mapper, capturingSubscriber)

        when:
        completionStageSubscriber.onSubscribe(subscription)
        completionStageSubscriber.onNext(0)

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.events == []

        when:
        completionStageSubscriber.onNext(1)

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.events == []

        when:
        completionStageSubscriber.onError(new RuntimeException("Bang"))

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.isCompletedExceptionally()
        // it immediately errored out
        capturingSubscriber.getThrowable().getMessage() == "Bang"
        capturingSubscriber.events == []

        when:
        // even if the promises later complete we are done
        promises.forEach { it.run() }

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.isCompletedExceptionally()
        capturingSubscriber.getThrowable().getMessage() == "Bang"
        capturingSubscriber.events == []
    }

    def "if onError is called, then futures are cancelled"() {
        def capturingSubscriber = new CapturingSubscriber<>()
        def subscription = new CapturingSubscription()
        List<CompletableFuture> promises = []
        Function<Integer, CompletionStage<String>> mapper = mapperThatDoesNotComplete([], promises)

        Subscriber<Integer> completionStageSubscriber = createSubscriber(mapper, capturingSubscriber)

        when:
        completionStageSubscriber.onSubscribe(subscription)
        completionStageSubscriber.onNext(0)
        completionStageSubscriber.onNext(1)
        completionStageSubscriber.onNext(2)
        completionStageSubscriber.onNext(3)
        completionStageSubscriber.onError(new RuntimeException("Bang"))

        then:
        !capturingSubscriber.isCompleted()
        capturingSubscriber.isCompletedExceptionally()
        capturingSubscriber.events == []

        promises.size() == 4
        for (CompletableFuture<?> cf : promises) {
            assert cf.isCancelled(), "The CF was not cancelled?"
        }
    }


    def "ordering test - depends on implementation"() {
        def capturingSubscriber = new CapturingSubscriber<>()
        def subscription = new CapturingSubscription()
        List<Runnable> promises = []
        Function<Integer, CompletionStage<String>> mapper = mapperThatDoesNotComplete(promises)

        Subscriber<Integer> completionStageSubscriber = createSubscriber(mapper, capturingSubscriber)

        when:
        completionStageSubscriber.onSubscribe(subscription)
        completionStageSubscriber.onNext(0)
        completionStageSubscriber.onNext(1)
        completionStageSubscriber.onNext(2)
        completionStageSubscriber.onNext(3)

        then:
        !subscription.isCancelled()
        !capturingSubscriber.isCompleted()
        capturingSubscriber.events == []

        when:
        completionStageSubscriber.onComplete()
        promises.reverse().forEach { it.run() }

        then:
        !subscription.isCancelled()
        capturingSubscriber.isCompleted()
        capturingSubscriber.events == expectedOrderingOfAsyncCompletion()
    }

    def "onNext is not signalled concurrently when promises complete on different threads"() {
        def active = new AtomicInteger()
        def maxActive = new AtomicInteger()
        def bothEntered = new CountDownLatch(2)
        def capturingSubscriber = new CapturingSubscriber<Object>() {
            @Override
            void onNext(Object o) {
                maxActive.accumulateAndGet(active.incrementAndGet(), { a, b -> Math.max(a, b) })
                bothEntered.countDown()
                // if the signals can overlap, the other thread enters onNext while this one waits
                bothEntered.await(500, TimeUnit.MILLISECONDS)
                active.decrementAndGet()
                super.onNext(o)
            }
        }
        def subscription = new CapturingSubscription()
        List<Runnable> promises = []
        Function<Integer, CompletionStage<String>> mapper = mapperThatDoesNotComplete(promises)

        Subscriber<Integer> completionStageSubscriber = createSubscriber(mapper, capturingSubscriber)

        when:
        completionStageSubscriber.onSubscribe(subscription)
        completionStageSubscriber.onNext(0)
        completionStageSubscriber.onNext(1)
        def threads = promises.collect { promise -> Thread.start { promise.run() } }
        threads.each { it.join() }

        then:
        maxActive.get() == 1
        capturingSubscriber.events.sort() == ["0", "1"]
    }

    def "onNext is not signalled after onError when a promise completes while another is being delivered"() {
        def deliveringZero = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def capturingSubscriber = new CapturingSubscriber<Object>() {
            @Override
            void onNext(Object o) {
                if (o == "0") {
                    deliveringZero.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                super.onNext(o)
            }
        }
        def subscription = new CapturingSubscription()
        List<Runnable> promises = []
        Function<Integer, CompletionStage<String>> mapper = mapperThatDoesNotComplete(promises)

        def completionStageSubscriber = createSubscriber(mapper, capturingSubscriber)

        when:
        completionStageSubscriber.onSubscribe(subscription)
        completionStageSubscriber.onNext(0)
        completionStageSubscriber.onNext(1)
        def completeZero = Thread.start { promises[0].run() }
        deliveringZero.await(5, TimeUnit.SECONDS)
        def completeOne = Thread.start { promises[1].run() }
        waitUntilWaitingOrDone(completeOne)
        def fail = Thread.start { completionStageSubscriber.onError(new RuntimeException("Bang")) }
        Awaitility.await().atMost(Duration.ofSeconds(5)).until { completionStageSubscriber.isTerminal() }
        waitUntilWaitingOrDone(fail)
        release.countDown()
        [completeZero, completeOne, fail].each { it.join() }

        then:
        capturingSubscriber.events == ["0"]
        capturingSubscriber.throwable.getMessage() == "Bang"
    }

    static void waitUntilWaitingOrDone(Thread thread) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until {
            thread.state == Thread.State.WAITING || thread.state == Thread.State.TERMINATED
        }
    }


    static Function<Integer, CompletionStage<String>> mapperThatDoesNotComplete(List<Runnable> runToComplete, List<CompletableFuture> promises = []) {
        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                def cf = new CompletableFuture<String>()
                runToComplete.add({ cf.complete(String.valueOf(integer)) })
                promises.add(cf)
                return cf
            }
        }
        mapper
    }

}
