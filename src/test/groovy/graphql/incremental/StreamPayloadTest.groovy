package graphql.incremental

import graphql.execution.ResultPath
import spock.lang.Specification

import static graphql.GraphqlErrorBuilder.newError

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
                .addError(newError()
                        .message("wow")
                        .build())
                .addErrors([
                        newError()
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
                .addError(newError()
                        .message("wow")
                        .build())
                .addErrors([])
                .errors([
                        newError()
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

    def "test equals and hashCode methods work"() {
        given:
        def items1 = ["test1"]
        def items2 = ["test2", "test3"]
        def path1 = ["test", "echo"]
        def path2 = ["test", "echo", "foo"]
        def errors1 = [newError().message("error1").build()]
        def errors2 = [newError().message("error2").build()]
        def extensions1 = [echo: "1"]
        def extensions2 = [echo: "2"]

        def payload = new StreamPayload.Builder()
                .items(items1)
                .path(path1)
                .label("label1")
                .errors(errors1)
                .extensions(extensions1)
                .build()

        def equivalentPayload = new StreamPayload.Builder()
                .items(items1)
                .path(path1)
                .label("label1")
                .errors(errors1)
                .extensions(extensions1)
                .build()

        def totallyDifferentPayload = new StreamPayload.Builder()
                .items(items2)
                .path(path2)
                .label("label2")
                .errors(errors2)
                .extensions(extensions2)
                .build()

        def slightlyDifferentPayload = new StreamPayload.Builder()
                .items(items2)
                .path(path2)
                .label("label1")
                .errors(errors1)
                .extensions(extensions2)
                .build()

        expect:
        payload == equivalentPayload
        payload != totallyDifferentPayload
        payload != slightlyDifferentPayload

        payload.hashCode() == equivalentPayload.hashCode()
        payload.hashCode() != totallyDifferentPayload.hashCode()
        payload.hashCode() != slightlyDifferentPayload.hashCode()
    }
}
