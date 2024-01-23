package graphql.execution.defer

import graphql.ExecutionResult
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
        List<ExecutionResult> results = []
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

        results.size() == 3
        results[0].incrementalItems[0].data["c"] == "C"
        results[1].incrementalItems[0].data["b"] == "B"
        results[2].incrementalItems[0].data["a"] == "A"
    }

    def "calls within calls are enqueued correctly"() {
        given:
        def deferContext = new DeferContext()
        deferContext.enqueue(offThreadCallWithinCall(deferContext, "A", "A_Child", 500, "/a"))
        deferContext.enqueue(offThreadCallWithinCall(deferContext, "B", "B_Child", 300, "/b"))
        deferContext.enqueue(offThreadCallWithinCall(deferContext, "C", "C_Child", 100, "/c"))

        when:
        List<ExecutionResult> results = []
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

        results.size() == 6
        results[0].incrementalItems[0].data["c"] == "C"
        results[1].incrementalItems[0].data["c_child"] == "C_Child"
        results[2].incrementalItems[0].data["b"] == "B"
        results[3].incrementalItems[0].data["a"] == "A"
        results[4].incrementalItems[0].data["b_child"] == "B_Child"
        results[5].incrementalItems[0].data["a_child"] == "A_Child"
    }

    def "stops at first exception encountered"() {
        given:
        def deferContext = new DeferContext()
        deferContext.enqueue(offThread("A", 100, "/field/path"))
        deferContext.enqueue(offThread("Bang", 50, "/field/path")) // <-- will throw exception
        deferContext.enqueue(offThread("C", 10, "/field/path"))

        when:
        List<ExecutionResult> results = []
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
        results[0].incrementalItems[0].data["c"] == "C"
    }

    def "you can cancel the subscription"() {
        given:
        def deferContext = new DeferContext()
        deferContext.enqueue(offThread("A", 100, "/field/path")) // <-- will finish last
        deferContext.enqueue(offThread("B", 50, "/field/path")) // <-- will finish second
        deferContext.enqueue(offThread("C", 10, "/field/path")) // <-- will finish first

        when:
        List<ExecutionResult> results = []
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
        results[0].incrementalItems[0].data["c"] == "C"
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

    }

    def "race condition"() {

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
}
