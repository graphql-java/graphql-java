package graphql.execution.defer

import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.GraphQLException
import graphql.language.Directive
import graphql.language.Field
import org.awaitility.Awaitility
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class DeferSupportTest extends Specification {

    def "emits N deferred calls in order"() {

        given:
        def deferSupport = new DeferSupport()
        deferSupport.enqueue(offThread("A", 100)) // <-- will finish last
        deferSupport.enqueue(offThread("B", 50)) // <-- will finish second
        deferSupport.enqueue(offThread("C", 10)) // <-- will finish first

        when:
        List<ExecutionResult> results = []
        AtomicBoolean finished = new AtomicBoolean()
        deferSupport.subscribe(new Subscriber<ExecutionResult>() {
            @Override
            void onSubscribe(Subscription subscription) {
                assert subscription != null
            }

            @Override
            void onNext(ExecutionResult executionResult) {
                results.add(executionResult)
            }

            @Override
            void onError(Throwable t) {
                assert false, "This should not be called!"
            }

            @Override
            void onComplete() {
                finished.set(true)
            }
        })

        Awaitility.await().untilTrue(finished)
        then:

        results.size() == 3
        results[0].data == "A"
        results[1].data == "B"
        results[2].data == "C"
    }

    def "stops at first exception encountered but in order"() {
        given:
        def deferSupport = new DeferSupport()
        deferSupport.enqueue(offThread("A", 100))
        deferSupport.enqueue(offThread("Bang", 50)) // <-- will throw exception
        deferSupport.enqueue(offThread("C", 10))

        when:
        List<ExecutionResult> results = []
        AtomicBoolean finished = new AtomicBoolean()
        Throwable thrown = null
        deferSupport.subscribe(new Subscriber<ExecutionResult>() {
            @Override
            void onSubscribe(Subscription subscription) {
                assert subscription != null
            }

            @Override
            void onNext(ExecutionResult executionResult) {
                results.add(executionResult)
            }

            @Override
            void onError(Throwable t) {
                thrown = t
                finished.set(true)
            }

            @Override
            void onComplete() {
                assert false, "This should not be called!"
            }
        })

        Awaitility.await().untilTrue(finished)
        then:

        results.size() == 1
        results[0].data == "A"
        thrown.message == "java.lang.RuntimeException: Bang"
    }

    def "you can cancel the subscription"() {
        given:
        def deferSupport = new DeferSupport()
        deferSupport.enqueue(offThread("A", 100)) // <-- will finish last
        deferSupport.enqueue(offThread("B", 50)) // <-- will finish second
        deferSupport.enqueue(offThread("C", 10)) // <-- will finish first

        when:
        List<ExecutionResult> results = []
        AtomicBoolean finished = new AtomicBoolean()
        deferSupport.subscribe(new Subscriber<ExecutionResult>() {
            Subscription savedSubscription

            @Override
            void onSubscribe(Subscription subscription) {
                assert subscription != null
                savedSubscription = subscription
            }

            @Override
            void onNext(ExecutionResult executionResult) {
                results.add(executionResult)
                savedSubscription.cancel()
            }

            @Override
            void onError(Throwable t) {
                assert false, "This should not be called!"
            }

            @Override
            void onComplete() {
                finished.set(true)
            }
        })

        Awaitility.await().untilTrue(finished)
        then:

        results.size() == 1
        results[0].data == "A"

    }

    def "you cant subscribe twice"() {
        given:
        def deferSupport = new DeferSupport()
        deferSupport.enqueue(offThread("A", 100))
        deferSupport.enqueue(offThread("Bang", 50)) // <-- will finish second
        deferSupport.enqueue(offThread("C", 10)) // <-- will finish first

        when:
        deferSupport.subscribe(noOpSubscriber())
        deferSupport.subscribe(noOpSubscriber())
        then:
        thrown(GraphQLException)
    }

    def "indicates of there any defers present"() {
        given:
        def deferSupport = new DeferSupport()

        when:
        def deferPresent1 = deferSupport.isDeferDetected()

        then:
        !deferPresent1

        when:
        deferSupport.enqueue(offThread("A", 100))
        def deferPresent2 = deferSupport.isDeferDetected()

        then:
        deferPresent2
    }

    def "detects @defer directive"() {
        given:
        def deferSupport = new DeferSupport()

        when:
        def noDirectivePresent = deferSupport.checkForDeferDirective([
                new Field("a"),
                new Field("b")
        ])

        then:
        !noDirectivePresent

        when:
        def directivePresent = deferSupport.checkForDeferDirective([
                new Field("a", [], [new Directive("defer")]),
                new Field("b")
        ])

        then:
        directivePresent


    }

    private static DeferredCall offThread(String data, int sleepTime) {
        def callSupplier = {
            CompletableFuture.supplyAsync({
                Thread.sleep(sleepTime)
                if (data == "Bang") {
                    throw new RuntimeException(data)
                }
                new ExecutionResultImpl(data, [])
            })
        }
        return new DeferredCall(callSupplier, new DeferredErrorSupport())
    }

    private static Subscriber<ExecutionResult> noOpSubscriber() {
        return new Subscriber<ExecutionResult>() {
            @Override
            void onSubscribe(Subscription s) {
            }

            @Override
            void onNext(ExecutionResult executionResult) {

            }

            @Override
            void onError(Throwable t) {
            }

            @Override
            void onComplete() {
            }
        }
    }
}
