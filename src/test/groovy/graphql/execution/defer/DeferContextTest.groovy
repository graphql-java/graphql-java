package graphql.execution.defer


import graphql.ExecutionResultImpl
import graphql.execution.ResultPath
import graphql.incremental.DelayedIncrementalExecutionResult
import org.awaitility.Awaitility
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

class DeferContextTest extends Specification {

    def "emits N deferred calls - ordering depends on call latency"() {
        given:
        def deferContext = new DeferContext()
        deferContext.enqueue(offThread("A", 100, "/field/path")) // <-- will finish last
        deferContext.enqueue(offThread("B", 50, "/field/path")) // <-- will finish second
        deferContext.enqueue(offThread("C", 10, "/field/path")) // <-- will finish first

        when:
        List<DelayedIncrementalExecutionResult> results = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(DelayedIncrementalExecutionResult executionResult) {
                results.add(executionResult)
                subscription.request(1)
            }
        }
        deferContext.startDeferredCalls().subscribe(subscriber)
        Awaitility.await().untilTrue(subscriber.finished)
        then:

        assertResultsSizeAndHasNextRule(3, results)
        results[0].incremental[0].data["c"] == "C"
        results[1].incremental[0].data["b"] == "B"
        results[2].incremental[0].data["a"] == "A"
    }

    def "calls within calls are enqueued correctly"() {
        given:
        def deferContext = new DeferContext()
        deferContext.enqueue(offThreadCallWithinCall(deferContext, "A", "A_Child", 500, "/a"))
        deferContext.enqueue(offThreadCallWithinCall(deferContext, "B", "B_Child", 300, "/b"))
        deferContext.enqueue(offThreadCallWithinCall(deferContext, "C", "C_Child", 100, "/c"))

        when:
        List<DelayedIncrementalExecutionResult> results = []
        BasicSubscriber subscriber = new BasicSubscriber() {
            @Override
            void onNext(DelayedIncrementalExecutionResult executionResult) {
                results.add(executionResult)
                subscription.request(1)
            }
        }
        deferContext.startDeferredCalls().subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.finished)
        then:

        assertResultsSizeAndHasNextRule(6, results)
        results[0].incremental[0].data["c"] == "C"
        results[1].incremental[0].data["c_child"] == "C_Child"
        results[2].incremental[0].data["b"] == "B"
        results[3].incremental[0].data["a"] == "A"
        results[4].incremental[0].data["b_child"] == "B_Child"
        results[5].incremental[0].data["a_child"] == "A_Child"
    }

    def "stops at first exception encountered"() {
        given:
        def deferContext = new DeferContext()
        deferContext.enqueue(offThread("A", 100, "/field/path"))
        deferContext.enqueue(offThread("Bang", 50, "/field/path")) // <-- will throw exception
        deferContext.enqueue(offThread("C", 10, "/field/path"))

        when:
        List<DelayedIncrementalExecutionResult> results = []
        Throwable thrown = null
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(DelayedIncrementalExecutionResult executionResult) {
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
        deferContext.startDeferredCalls().subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.finished)
        then:

        thrown.message == "java.lang.RuntimeException: Bang"
        results[0].incremental[0].data["c"] == "C"
    }

    def "you can cancel the subscription"() {
        given:
        def deferContext = new DeferContext()
        deferContext.enqueue(offThread("A", 100, "/field/path")) // <-- will finish last
        deferContext.enqueue(offThread("B", 50, "/field/path")) // <-- will finish second
        deferContext.enqueue(offThread("C", 10, "/field/path")) // <-- will finish first

        when:
        List<DelayedIncrementalExecutionResult> results = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(DelayedIncrementalExecutionResult executionResult) {
                results.add(executionResult)
                subscription.cancel()
                finished.set(true)
            }
        }
        deferContext.startDeferredCalls().subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.finished)
        then:

        results.size() == 1
        results[0].incremental[0].data["c"] == "C"
        // Cancelling the subscription will result in an invalid state. The last result item will have "hasNext=true".
        results[0].hasNext
    }

    def "you cant subscribe twice"() {
        given:
        def deferContext = new DeferContext()
        deferContext.enqueue(offThread("A", 100, "/field/path"))
        deferContext.enqueue(offThread("Bang", 50, "/field/path")) // <-- will finish second
        deferContext.enqueue(offThread("C", 10, "/field/path")) // <-- will finish first

        when:
        Throwable expectedThrowable
        deferContext.startDeferredCalls().subscribe(new BasicSubscriber())
        deferContext.startDeferredCalls().subscribe(new BasicSubscriber() {
            @Override
            void onError(Throwable t) {
                expectedThrowable = t
            }
        })
        then:
        expectedThrowable != null
    }

    def "indicates if there are any defers present"() {
        given:
        def deferContext = new DeferContext()

        when:
        def deferPresent1 = deferContext.isDeferDetected()

        then:
        !deferPresent1

        when:
        deferContext.enqueue(offThread("A", 100, "/field/path"))
        def deferPresent2 = deferContext.isDeferDetected()

        then:
        deferPresent2
    }

    def "multiple fields are part of the same call"() {
        given: "a DeferredCall that contains resolution of multiple fields"
        def call1 = new Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>() {
            @Override
            CompletableFuture<DeferredCall.FieldWithExecutionResult> get() {
                return CompletableFuture.supplyAsync({
                    Thread.sleep(10)
                    new DeferredCall.FieldWithExecutionResult("call1", new ExecutionResultImpl("Call 1", []))
                })
            }
        }

        def call2 = new Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>() {
            @Override
            CompletableFuture<DeferredCall.FieldWithExecutionResult> get() {
                return CompletableFuture.supplyAsync({
                    Thread.sleep(100)
                    new DeferredCall.FieldWithExecutionResult("call2", new ExecutionResultImpl("Call 2", []))
                })
            }
        }

        def deferredCall = new DeferredCall(null, ResultPath.parse("/field/path"), [call1, call2], new DeferredErrorSupport())

        when:
        def deferContext = new DeferContext()
        deferContext.enqueue(deferredCall)

        List<DelayedIncrementalExecutionResult> results = []
        BasicSubscriber subscriber = new BasicSubscriber() {
            @Override
            void onNext(DelayedIncrementalExecutionResult executionResult) {
                results.add(executionResult)
                subscription.request(1)
            }
        }
        deferContext.startDeferredCalls().subscribe(subscriber)

        Awaitility.await().untilTrue(subscriber.finished)
        then:
        assertResultsSizeAndHasNextRule(1, results)
        results[0].incremental[0].data["call1"] == "Call 1"
        results[0].incremental[0].data["call2"] == "Call 2"
    }

    def "race conditions should not impact the calculation of the hasNext value"() {
        given: "calls that have the same sleepTime"
        def deferContext = new DeferContext()
        deferContext.enqueue(offThread("A", 10, "/field/path")) // <-- will finish last
        deferContext.enqueue(offThread("B", 10, "/field/path")) // <-- will finish second
        deferContext.enqueue(offThread("C", 10, "/field/path")) // <-- will finish first

        when:
        List<DelayedIncrementalExecutionResult> results = []
        def subscriber = new BasicSubscriber() {
            @Override
            void onNext(DelayedIncrementalExecutionResult executionResult) {
                results.add(executionResult)
                subscription.request(1)
            }
        }
        deferContext.startDeferredCalls().subscribe(subscriber)
        Awaitility.await().untilTrue(subscriber.finished)

        then: "hasNext placement should be deterministic - only the last event published should have 'hasNext=true'"
        assertResultsSizeAndHasNextRule(3, results)

        then: "but the actual order or publish events is non-deterministic - they all have the same latency (sleepTime)."
        results.any { it.incremental[0].data["a"] == "A" }
        results.any { it.incremental[0].data["b"] == "B" }
        results.any { it.incremental[0].data["c"] == "C" }
    }

    private static DeferredCall offThread(String data, int sleepTime, String path) {
        def callSupplier = new Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>() {
            @Override
            CompletableFuture<DeferredCall.FieldWithExecutionResult> get() {
                return CompletableFuture.supplyAsync({
                    Thread.sleep(sleepTime)
                    if (data == "Bang") {
                        throw new RuntimeException(data)
                    }
                    new DeferredCall.FieldWithExecutionResult(data.toLowerCase(), new ExecutionResultImpl(data, []))
                })
            }
        }

        return new DeferredCall(null, ResultPath.parse(path), [callSupplier], new DeferredErrorSupport())
    }

    private static DeferredCall offThreadCallWithinCall(DeferContext deferContext, String dataParent, String dataChild, int sleepTime, String path) {
        def callSupplier = new Supplier<CompletableFuture<DeferredCall.FieldWithExecutionResult>>() {
            @Override
            CompletableFuture<DeferredCall.FieldWithExecutionResult> get() {
                CompletableFuture.supplyAsync({
                    Thread.sleep(sleepTime)
                    deferContext.enqueue(offThread(dataChild, sleepTime, path))
                    new DeferredCall.FieldWithExecutionResult(dataParent.toLowerCase(), new ExecutionResultImpl(dataParent, []))
                })
            }
        }
        return new DeferredCall(null, ResultPath.parse("/field/path"), [callSupplier], new DeferredErrorSupport())
    }


    private static void assertResultsSizeAndHasNextRule(int expectedSize, List<DelayedIncrementalExecutionResult> results) {
        assert results.size() == expectedSize

        for (def i = 0; i < results.size(); i++) {
            def isLastResult = i == results.size() - 1
            def hasNext = results[i].hasNext()

            assert (hasNext && !isLastResult)
                    || (!hasNext && isLastResult)
        }
    }
}
