package graphql.incremental

import graphql.execution.ResultPath
import spock.lang.Specification

import static graphql.incremental.DeferPayload.newDeferredItem
import static graphql.incremental.IncrementalExecutionResultImpl.newIncrementalExecutionResult
import static graphql.incremental.StreamPayload.newStreamedItem

class IncrementalExecutionResultTest extends Specification {

    def "sanity test to check builders work"() {
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
                .build()

        def toSpec = result.toSpecification()

        then:
        toSpec == [
                data       : [person: [name: "Luke Skywalker", films: [[title: "A New Hope"]]]],
                hasNext    : true,
                incremental: [
                        [path: ["person"], label: "homeWorldDefer", data: [homeWorld: "Tatooine"]],
                        [path: ["person", "films", 1], label: "filmsStream", items: [[title: "The Empire Strikes Back"]]],
                        [path: ["person", "films", 2], label: "filmsStream", items: [[title: "Return of the Jedi"]]],
                ]
        ]

    }
}
