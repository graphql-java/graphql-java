package graphql.execution.reactive

import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.pubsub.CapturingSubscriber
import graphql.execution.pubsub.CountingFlux
import graphql.schema.DataFetcher
import reactor.adapter.JdkFlowAdapter
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Flow

class ReactiveSupportTest extends Specification {

    private static Flow.Publisher<String> toFlow(Mono<String> stringMono) {
        return JdkFlowAdapter.publisherToFlowPublisher(stringMono)
    }

    private static Mono<String> delayedMono(String X, Integer millis) {
        Mono.just(X).delayElement(Duration.ofMillis(millis))
    }

    def "will pass through non reactive things and leave them as is"() {

        when:
        def val = ReactiveSupport.fetchedObject("X")
        then:
        val === "X"

        when:
        def cf = CompletableFuture.completedFuture("X")
        val = ReactiveSupport.fetchedObject(cf)
        then:
        val === cf
    }

    def "can get a reactive or flow publisher and make a CF from it"() {

        when:
        CompletableFuture<?> cf = ReactiveSupport.fetchedObject(reactiveObject) as CompletableFuture<?>

        then:
        !cf.isCancelled()
        !cf.isCompletedExceptionally()
        cf.isDone()

        cf.join() == "X"

        where:
        reactiveObject         || _
        Mono.just("X")         || _
        toFlow(Mono.just("X")) || _
    }

    def "can get a reactive or flow publisher that takes some time and make a CF from it"() {

        when:
        CompletableFuture<?> cf = ReactiveSupport.fetchedObject(reactiveObject) as CompletableFuture<?>

        then:
        !cf.isCancelled()
        !cf.isCompletedExceptionally()
        !cf.isDone()

        cf.join() == "X"

        where:
        reactiveObject                || _
        delayedMono("X", 100)         || _
        toFlow(delayedMono("X", 100)) || _
    }

    def "can get a reactive or flow publisher with an error and make a CF from it"() {

        when:
        CompletableFuture<?> cf = ReactiveSupport.fetchedObject(reactiveObject) as CompletableFuture<?>

        then:
        !cf.isCancelled()
        cf.isCompletedExceptionally()
        cf.isDone()

        when:
        cf.join()

        then:
        def e = thrown(CompletionException.class)
        e.cause.message == "Bang!"

        where:
        reactiveObject                                    || _
        Mono.error(new RuntimeException("Bang!"))         || _
        toFlow(Mono.error(new RuntimeException("Bang!"))) || _
    }

    def "can get a empty reactive or flow publisher and make a CF from it"() {

        when:
        CompletableFuture<?> cf = ReactiveSupport.fetchedObject(reactiveObject) as CompletableFuture<?>

        then:
        !cf.isCancelled()
        !cf.isCompletedExceptionally()
        cf.isDone()

        cf.join() == null

        where:
        reactiveObject         || _
        Mono.empty()         || _
        toFlow(Mono.empty()) || _
    }


    def "can get a reactive or flow publisher but cancel it before a value turns up"() {

        when:
        CompletableFuture<?> cf = ReactiveSupport.fetchedObject(reactiveObject) as CompletableFuture<?>

        then:
        !cf.isCancelled()
        !cf.isCompletedExceptionally()
        !cf.isDone()

        when:
        def cfCancelled = cf.cancel(true)

        then:
        cfCancelled
        cf.isCancelled()
        cf.isCompletedExceptionally()
        cf.isDone()

        when:
        cf.join()

        then:
        thrown(CancellationException.class)

        where:
        reactiveObject                 || _
        delayedMono("X", 1000)         || _
        toFlow(delayedMono("X", 1000)) || _

    }


    def "can get a reactive Flux and only take one value and make a CF from it"() {

        def xyzStrings = ["X", "Y", "Z"]
        when:
        def countingFlux = new CountingFlux(xyzStrings)
        CompletableFuture<?> cf = ReactiveSupport.fetchedObject(countingFlux.flux) as CompletableFuture<?>

        then:
        !cf.isCancelled()
        !cf.isCompletedExceptionally()
        cf.isDone()

        cf.join() == "X"
        countingFlux.count == 1

        when:
        def capturingSubscriber = new CapturingSubscriber<>()
        countingFlux.flux.subscribe(capturingSubscriber)

        then:
        // second subscriber
        capturingSubscriber.events == ["X", "Y", "Z"]
        countingFlux.count == 4
    }

    def "integration test showing reactive values in data fetchers as well as the ones we know and love"() {
        def sdl = """
            type Query {
                reactorField : String
                flowField : String
                cfField : String
                materialisedField : String
            }
        """

        // with some delay
        def reactorDF = { env -> delayedMono("reactor", 100) } as DataFetcher
        def flowDF = { env -> toFlow(delayedMono("flow", 50)) } as DataFetcher

        def cfDF = { env -> CompletableFuture.completedFuture("cf") } as DataFetcher
        def materialisedDF = { env -> "materialised" } as DataFetcher

        def schema = TestUtil.schema(sdl, [Query: [
                reactorField     : reactorDF,
                flowField        : flowDF,
                cfField          : cfDF,
                materialisedField: materialisedDF,
        ]])
        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        def er = graphQL.execute("""
            query q {
                reactorField
                flowField
                cfField
                materialisedField
            }
        """)

        then:
        er.errors.isEmpty()
        er.data == [
                reactorField     : "reactor",
                flowField        : "flow",
                cfField          : "cf",
                materialisedField: "materialised"
        ]
    }
}
