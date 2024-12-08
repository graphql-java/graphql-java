package graphql.incremental

import graphql.GraphqlErrorBuilder
import graphql.execution.ResultPath
import spock.lang.Specification

import static graphql.GraphQLError.newError

class DeferPayloadTest extends Specification {
    def "null data is included"() {
        def payload = DeferPayload.newDeferredItem()
                .data(null)
                .build()

        when:
        def spec = payload.toSpecification()

        then:
        spec == [
                data: null,
                path: null,
        ]
    }

    def "can construct an instance using builder"() {
        def payload = DeferPayload.newDeferredItem()
                .data("twow is that a bee")
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
                data      : "twow is that a bee",
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
        def payload = DeferPayload.newDeferredItem()
                .data("twow is that a bee")
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
                data      : "twow is that a bee",
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

    def "equals and hashcode methods work correctly"() {
        when:
        def payload = DeferPayload.newDeferredItem()
                .data("data1")
                .path(["path1"])
                .label("label1")
                .errors([newError().message("message1").build()])
                .extensions([key: "value1"])
                .build()

        def equivalentPayload = DeferPayload.newDeferredItem()
                .data("data1")
                .path(["path1"])
                .label("label1")
                .errors([newError().message("message1").build()])
                .extensions([key: "value1"])
                .build()

        def totallyDifferentPayload = DeferPayload.newDeferredItem()
                .data("data2")
                .path(["path2"])
                .label("label2")
                .errors([newError().message("message2").build()])
                .extensions([key: "value2"])
                .build()

        def slightlyDifferentPayload = DeferPayload.newDeferredItem()
                .data("data1")
                .path(["path1"])
                .label("label1")
                .errors([newError().message("message1").build()])
                .extensions([key: "value2"])
                .build()

        then:
        payload == equivalentPayload
        payload != totallyDifferentPayload
        payload != slightlyDifferentPayload

        payload.hashCode() == equivalentPayload.hashCode()
        payload.hashCode() != totallyDifferentPayload.hashCode()
        payload.hashCode() != slightlyDifferentPayload.hashCode()
    }
}
