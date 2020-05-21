package graphql.execution.reactive

import graphql.execution.pubsub.CapturingSubscriber
import io.reactivex.Flowable
import org.awaitility.Awaitility
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
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
        capturingSubscriber.events[0] instanceof String
        capturingSubscriber.events[0] == "0"
    }

    def "multiple subscribers get there messages"() {

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
        capturingSubscriber2.events.size() == 10
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
        capturingSubscriber.events[0] instanceof String
        capturingSubscriber.events[0] == "0"
    }

    Function<Integer, CompletionStage<String>> mapperThatDelaysFor(int delay) {
        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                return CompletableFuture.supplyAsync({
                    Thread.sleep(delay)
                    return String.valueOf(integer)
                })
            }
        }
        mapper
    }

}
