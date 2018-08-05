package graphql.execution.defer

import graphql.ExecutionResult
import graphql.ExecutionResultImpl
import graphql.execution.lazy.LazySupport
import graphql.language.Directive
import graphql.language.Field
import org.awaitility.Awaitility
import org.reactivestreams.Subscription
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class DeferSupportTest extends Specification {


    def "emits N deferred calls with order preserved"() {

        given:
        def deferSupport = new DeferSupport(new LazySupport())
        deferSupport.enqueue(offThread("A", 100)) // <-- will finish last
        deferSupport.enqueue(offThread("B", 50)) // <-- will finish second
        deferSupport.enqueue(offThread("C", 10)) // <-- will finish first

        when:
        List<ExecutionResult> results = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult executionResult) {
                results.add(executionResult)
                subscription.request(1)
            }
        }
        deferSupport.startDeferredCalls().subscribe(subscriber)
        Awaitility.await().untilTrue(subscriber.finished)
        then:

        results.size() == 3
        results[0].data == "A"
        results[1].data == "B"
        results[2].data == "C"
    }

    def "calls within calls are enqueued correctly"() {
        given:
        def deferSupport = new DeferSupport(new LazySupport())
        deferSupport.enqueue(offThreadCallWithinCall(deferSupport, "A", "a", 100))
        deferSupport.enqueue(offThreadCallWithinCall(deferSupport, "B", "b", 50))
        deferSupport.enqueue(offThreadCallWithinCall(deferSupport, "C", "c", 10))

        when:
        List<ExecutionResult> results = []
        BasicSubscriber subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult executionResult) {
                results.add(executionResult)
                subscription.request(1)
            }
        }
        deferSupport.startDeferredCalls().subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.finished)
        then:

        results.size() == 6
        results[0].data == "A"
        results[1].data == "B"
        results[2].data == "C"
        results[3].data == "a"
        results[4].data == "b"
        results[5].data == "c"
    }

    def "stops at first exception encountered"() {
        given:
        def deferSupport = new DeferSupport(new LazySupport())
        deferSupport.enqueue(offThread("A", 100))
        deferSupport.enqueue(offThread("Bang", 50)) // <-- will throw exception
        deferSupport.enqueue(offThread("C", 10))

        when:
        List<ExecutionResult> results = []
        Throwable thrown = null
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult executionResult) {
                results.add(executionResult)
                subscription.request(1)
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
        }
        deferSupport.startDeferredCalls().subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.finished)
        then:

        thrown.message == "java.lang.RuntimeException: Bang"
    }

    def "you can cancel the subscription"() {
        given:
        def deferSupport = new DeferSupport(new LazySupport())
        deferSupport.enqueue(offThread("A", 100)) // <-- will finish last
        deferSupport.enqueue(offThread("B", 50)) // <-- will finish second
        deferSupport.enqueue(offThread("C", 10)) // <-- will finish first

        when:
        List<ExecutionResult> results = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(ExecutionResult executionResult) {
                results.add(executionResult)
                subscription.cancel()
                finished.set(true)
            }
        }
        deferSupport.startDeferredCalls().subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.finished)
        then:

        results.size() == 1
        results[0].data == "A"

    }

    def "you cant subscribe twice"() {
        given:
        def deferSupport = new DeferSupport(new LazySupport())
        deferSupport.enqueue(offThread("A", 100))
        deferSupport.enqueue(offThread("Bang", 50)) // <-- will finish second
        deferSupport.enqueue(offThread("C", 10)) // <-- will finish first

        when:
        Throwable expectedThrowble
        deferSupport.startDeferredCalls().subscribe(new BasicSubscriber())
        deferSupport.startDeferredCalls().subscribe(new BasicSubscriber() {
            @Override
            void onError(Throwable t) {
                expectedThrowble = t
            }
        })
        then:
        expectedThrowble != null
    }

    def "indicates of there any defers present"() {
        given:
        def deferSupport = new DeferSupport(new LazySupport())

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
        def deferSupport = new DeferSupport(new LazySupport())

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

    def "lazy support restarts defer dispatch"() {
        given:
        def lazySupport = new LazySupport()
        def token = lazySupport.addLazyCompletion()
        def deferSupport = new DeferSupport(lazySupport)
        def future = enqueueFuture(deferSupport)
        future.complete(new ExecutionResultImpl("A", null))

        def results = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(ExecutionResult executionResult) {
                results.add(executionResult.data)
            }
        }

        when:
        deferSupport.startDeferredCalls().subscribe(subscriber)

        then:
        results == [ "A" ]

        when:
        future = enqueueFuture(deferSupport)
        future.complete(new ExecutionResultImpl("B", null))

        then:
        results == [ "A" ]

        when:
        token.release()

        then:
        results == [ "A", "B" ]
    }

    def enqueueFuture(def deferSupport) {
        def future = new CompletableFuture()
        deferSupport.enqueue(new DeferredCall({ future }, new DeferredErrorSupport()))
        return future
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

    private
    static DeferredCall offThreadCallWithinCall(DeferSupport deferSupport, String dataParent, String dataChild, int sleepTime) {
        def callSupplier = {
            CompletableFuture.supplyAsync({
                Thread.sleep(sleepTime)
                deferSupport.enqueue(offThread(dataChild, sleepTime))
                new ExecutionResultImpl(dataParent, [])
            })
        }
        return new DeferredCall(callSupplier, new DeferredErrorSupport())
    }
}
