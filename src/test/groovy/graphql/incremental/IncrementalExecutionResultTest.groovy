package graphql.incremental

import graphql.execution.ResultPath
import io.reactivex.Flowable
import spock.lang.Specification

import static graphql.incremental.DeferPayload.newDeferredItem
import static graphql.incremental.IncrementalExecutionResultImpl.newIncrementalExecutionResult
import static graphql.incremental.StreamPayload.newStreamedItem

class IncrementalExecutionResultTest extends Specification {

    def "sanity test to check IncrementalExecutionResultImpl builder and item builders work"() {
        when:
        def defer1 = newDeferredItem()
                .label("homeWorldDefer")
                .path(ResultPath.parse("/person"))
                .data([homeWorld: "Tatooine"])
                .build()

        def stream1 = newStreamedItem()
                .label("filmsStream")
                .path(ResultPath.parse("/person/films[1]"))
                .items([[title: "The Empire Strikes Back"]])
                .build()

        def stream2 = newStreamedItem()
                .label("filmsStream")
                .path(ResultPath.parse("/person/films[2]"))
                .items([[title: "Return of the Jedi"]])
                .build()

        def result = newIncrementalExecutionResult()
                .data([
                        person: [
                                name : "Luke Skywalker",
                                films: [
                                        [title: "A New Hope"]
                                ]
                        ]
                ])
                .hasNext(true)
                .incremental([defer1, stream1, stream2])
                .extensions([some: "map"])
                .build()

        def toSpec = result.toSpecification()

        then:
        toSpec == [
                data       : [person: [name: "Luke Skywalker", films: [[title: "A New Hope"]]]],
                extensions: [some: "map"],
                hasNext    : true,
                incremental: [
                        [path: ["person"], label: "homeWorldDefer", data: [homeWorld: "Tatooine"]],
                        [path: ["person", "films", 1], label: "filmsStream", items: [[title: "The Empire Strikes Back"]]],
                        [path: ["person", "films", 2], label: "filmsStream", items: [[title: "Return of the Jedi"]]],
                ]
        ]

    }
    def "sanity test to check DelayedIncrementalPartialResult builder works"() {
        when:
        def deferredItem = newDeferredItem()
                .label("homeWorld")
                .path(ResultPath.parse("/person"))
                .data([homeWorld: "Tatooine"])
                .build()

        def result = DelayedIncrementalPartialResultImpl.newIncrementalExecutionResult()
                .incrementalItems([deferredItem])
                .hasNext(false)
                .extensions([some: "map"])
                .build()

        def toSpec = result.toSpecification()

        then:
        toSpec == [
                incremental: [[path: ["person"], label: "homeWorld", data: [homeWorld: "Tatooine"]]],
                extensions: [some: "map"],
                hasNext    : false,
        ]
    }

    def "sanity test to check Builder.from works"() {

        when:
        def defer1 = newDeferredItem()
                .label("homeWorldDefer")
                .path(ResultPath.parse("/person"))
                .data([homeWorld: "Tatooine"])
                .build()


        def incrementalExecutionResult = new IncrementalExecutionResultImpl.Builder()
                .data([
                        person: [
                                name : "Luke Skywalker",
                                films: [
                                        [title: "A New Hope"]
                                ]
                        ]
                ])
                .incrementalItemPublisher { Flowable.range(1,10)}
                .hasNext(true)
                .incremental([defer1])
                .extensions([some: "map"])
                .build()

        def newIncrementalExecutionResult = new IncrementalExecutionResultImpl.Builder().from(incrementalExecutionResult).build()

        then:
        newIncrementalExecutionResult.incremental == incrementalExecutionResult.incremental
        newIncrementalExecutionResult.extensions == incrementalExecutionResult.extensions
        newIncrementalExecutionResult.errors == incrementalExecutionResult.errors
        newIncrementalExecutionResult.incrementalItemPublisher == incrementalExecutionResult.incrementalItemPublisher
        newIncrementalExecutionResult.hasNext() == incrementalExecutionResult.hasNext()
        newIncrementalExecutionResult.toSpecification() == incrementalExecutionResult.toSpecification()
    }

    def "transform returns IncrementalExecutionResult"() {
        when:
        def initial = newIncrementalExecutionResult().hasNext(true).build()

        then:
        def transformed = initial.transform { b ->
            b.addExtension("ext-key", "ext-value")
            b.hasNext(false)
        }
        transformed instanceof IncrementalExecutionResult
        transformed.extensions == ["ext-key": "ext-value"]
        transformed.hasNext == false
    }
}
