package graphql.execution.reactive

import graphql.execution.pubsub.CapturingSubscriber
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

class CompletionStageMappingPublisherTest extends Specification {

    def "basic mapping"() {

        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)
        Executor executor = CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)

        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                return CompletableFuture.supplyAsync(new Supplier<String>() {
                    @Override
                    String get() {
                        return String.valueOf(integer)
                    }
                }, executor)
            }
        }
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber = new CapturingSubscriber<>()
        rxStrings.subscribe(capturingSubscriber)
        capturingSubscriber.done()
        then:

        capturingSubscriber.events.size() == 10
        capturingSubscriber.events[0] instanceof String
        capturingSubscriber.events[0] == "0"
    }

    def "multiple subscribers get there messages"() {

        when:
        Publisher<Integer> rxIntegers = Flowable.range(0, 10)
        Executor executor = CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS)

        def mapper = new Function<Integer, CompletionStage<String>>() {
            @Override
            CompletionStage<String> apply(Integer integer) {
                return CompletableFuture.supplyAsync(new Supplier<String>() {
                    @Override
                    String get() {
                        return String.valueOf(integer)
                    }
                }, executor)
            }
        }
        Publisher<String> rxStrings = new CompletionStageMappingPublisher<String, Integer>(rxIntegers, mapper)

        def capturingSubscriber1 = new CapturingSubscriber<>()
        def capturingSubscriber2 = new CapturingSubscriber<>()
        rxStrings.subscribe(capturingSubscriber1)
        rxStrings.subscribe(capturingSubscriber2)
        capturingSubscriber1.done()
        capturingSubscriber2.done()
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

}
