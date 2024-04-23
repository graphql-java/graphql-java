package graphql.execution.reactive

import graphql.TestUtil
import graphql.execution.pubsub.CapturingSubscriber
import io.reactivex.Flowable
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Function

class CompletionStageMappingPublisherTest extends Specification {

    def "basic mapping"() {

        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)

        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                return CompletableFuture.completedFuture(String.valueOf(integer))
            }
        }
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber = new CapturingSubscriber<>()
        rxStrings.subscribe(capturingSubscriber)

        then:

        capturingSubscriber.events.size() == 10
        // order is kept
        capturingSubscriber.events == ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9",]
    }

    def "multiple subscribers get their messages"() {

        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)

        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                return CompletableFuture.completedFuture(String.valueOf(integer))
            }
        }
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber1 = new CapturingSubscriber<>()
        def capturingSubscriber2 = new CapturingSubscriber<>()
        rxStrings.subscribe(capturingSubscriber1)
        rxStrings.subscribe(capturingSubscriber2)

        then:

        capturingSubscriber1.events.size() == 10
        // order is kept
        capturingSubscriber1.events == ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9",]

        capturingSubscriber2.events.size() == 10
        // order is kept
        capturingSubscriber2.events == ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9",]
    }

    def "error handling"() {
        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)

        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {

                if (integer == 5) {
                    def future = new CompletableFuture()
                    future.completeExceptionally(new RuntimeException("Bang"))
                    return future
                } else {
                    CompletableFuture.completedFuture(String.valueOf(integer))
                }
            }
        }
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber = new CapturingSubscriber<>()
        rxStrings.subscribe(capturingSubscriber)

        then:

        capturingSubscriber.throwable.getMessage() == "Bang"
        //
        // got this far and cancelled
        capturingSubscriber.events.size() == 5
        capturingSubscriber.events == ["0", "1", "2", "3", "4",]

    }

    def "error handling when the error happens in the middle of the processing but before the others complete"() {
        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)

        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {

                if (integer == 5) {
                    return CompletableFuture.supplyAsync {
                        throw new RuntimeException("Bang")
                    }
                } else {
                    return asyncValueAfterDelay(10, integer)
                }
            }
        }
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber = new CapturingSubscriber<>(10)
        rxStrings.subscribe(capturingSubscriber)

        then:
        Awaitility.await().untilTrue(capturingSubscriber.isDone())

        unwrap(capturingSubscriber.throwable).getMessage() == "Bang"
    }

    def "error handling when the error happens in the middle of the processing but after the previous ones complete"() {
        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)

        CountDownLatch latch = new CountDownLatch(5)
        CountDownLatch exceptionLatch = new CountDownLatch(1)
        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {

                if (integer == 5) {
                    return CompletableFuture.supplyAsync {
                        exceptionLatch.await(10_000, TimeUnit.SECONDS)
                        sleep(100)
                        throw new RuntimeException("Bang")
                    }
                } else if (integer == 6) {
                    return CompletableFuture.supplyAsync {
                        latch.countDown()
                        exceptionLatch.countDown() // number 5 is now alive
                        return String.valueOf(integer)
                    }
                } else {
                    return CompletableFuture.supplyAsync {
                        latch.countDown()
                        latch.await(10_000, TimeUnit.SECONDS)
                        return String.valueOf(integer)
                    }
                }
            }
        }
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber = new CapturingSubscriber<>(10)
        rxStrings.subscribe(capturingSubscriber)

        then:
        Awaitility.await().untilTrue(capturingSubscriber.isDone())

        unwrap(capturingSubscriber.throwable).getMessage() == "Bang"
    }


    def "mapper exception causes onError"() {
        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)

        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {

                if (integer == 5) {
                    throw new RuntimeException("Bang")
                } else {
                    CompletableFuture.completedFuture(String.valueOf(integer))
                }
            }
        }
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber = new CapturingSubscriber<>()
        rxStrings.subscribe(capturingSubscriber)

        then:

        capturingSubscriber.throwable.getMessage() == "Bang"
        //
        // got this far and cancelled
        capturingSubscriber.events.size() == 5
        capturingSubscriber.events == ["0", "1", "2", "3", "4",]
    }


    def "asynchronous mapping works with completion"() {

        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)

        Function<Integer, CompletionStage<String>> mapper = mapperThatDelaysFor(100)
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber = new CapturingSubscriber<>()
        rxStrings.subscribe(capturingSubscriber)

        then:

        Awaitility.await().untilTrue(capturingSubscriber.isDone())

        capturingSubscriber.events.size() == 10
        // order is kept
        capturingSubscriber.events == ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9",]
    }

    def "asynchronous mapping works when they complete out of order"() {

        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)

        Function<Integer, CompletionStage<String>> mapper = mapperThatRandomlyDelaysFor(5, 15)
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        // ask for 10 at a time to create some forward pressure
        def capturingSubscriber = new CapturingSubscriber<>(10)
        rxStrings.subscribe(capturingSubscriber)

        then:

        Awaitility.await().untilTrue(capturingSubscriber.isDone())

        capturingSubscriber.events.size() == 10
        // the original flow order was kept
        capturingSubscriber.events == ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9",]
    }

    Function<Integer, CompletionStage<String>> mapperThatDelaysFor(int delay) {
        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                return CompletableFuture.supplyAsync({
                    Thread.sleep(delay)
                    println "\t\tcompleted : " + integer
                    return String.valueOf(integer)
                })
            }
        }
        mapper
    }

    Function<Integer, CompletionStage<String>> mapperThatRandomlyDelaysFor(int delayMin, int delayMax) {
        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                return asyncValueAfterDelay(TestUtil.rand(delayMin, delayMax), integer)
            }
        }
        mapper
    }

    private static CompletableFuture<String> asyncValueAfterDelay(int delay, int integer) {
        return CompletableFuture.supplyAsync({
            Thread.sleep(delay)
            println "\t\tcompleted : " + integer
            return String.valueOf(integer)
        })
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException) {
            return ((CompletionException) throwable).getCause()
        }
        return throwable
    }
}
