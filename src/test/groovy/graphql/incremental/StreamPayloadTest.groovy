package graphql.incremental

import graphql.GraphqlErrorBuilder
import graphql.execution.ResultPath
import spock.lang.Specification

class StreamPayloadTest extends Specification {
    def "null data is included"() {
        def payload = StreamPayload.newStreamedItem()
                .items(null)
                .build()

        when:
        def spec = payload.toSpecification()

        then:
        spec == [
                items: null,
                path : null,
                ]
    }
    def "can construct an instance using builder"() {
        def payload = StreamPayload.newStreamedItem()
                .items(["twow is that a bee"])
                .path(["hello"])
                .errors([])
                .addError(GraphqlErrorBuilder.newError()
                        .message("wow")
                        .build())
                .addErrors([
                        GraphqlErrorBuilder.newError()
                                .message("yep")
                                .build(),
                ])
                .extensions([echo: "Hello world"])
                .build()

        when:
        def serialized = payload.toSpecification()

        then:
        serialized == [
                items     : ["twow is that a bee"],
                path      : ["hello"],
                errors    : [
                        [
                                message   : "wow",
                                locations : [],
                                extensions: [classification: "DataFetchingException"],
                        ],
                        [
                                message   : "yep",
                                locations : [],
                                extensions: [classification: "DataFetchingException"],
                        ],
                ],
                extensions: [
                        echo: "Hello world",
                ],
        ]
    }

    def "errors replaces existing errors"() {
        def payload = StreamPayload.newStreamedItem()
                .items(["twow is that a bee"])
                .path(ResultPath.fromList(["test", "echo"]))
                .addError(GraphqlErrorBuilder.newError()
                        .message("wow")
                        .build())
                .addErrors([])
                .errors([
                        GraphqlErrorBuilder.newError()
                                .message("yep")
                                .build(),
                ])
                .extensions([echo: "Hello world"])
                .build()

        when:
        def serialized = payload.toSpecification()

        then:
        serialized == [
                items     : ["twow is that a bee"],
                errors    : [
                        [
                                message   : "yep",
                                locations : [],
                                extensions: [classification: "DataFetchingException"],
                        ],
                ],
                extensions: [
                        echo: "Hello world",
                ],
                path      : ["test", "echo"],
        ]
    }
}
