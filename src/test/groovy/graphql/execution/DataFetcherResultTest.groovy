package graphql.execution

import graphql.GraphQLError
import graphql.InvalidSyntaxError
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class DataFetcherResultTest extends Specification {

    def error1 = ValidationError.newValidationError().validationErrorType(ValidationErrorType.DuplicateOperationName).description("Duplicate operation name").build()
    def error2 = new InvalidSyntaxError([], "Boo")

    def "basic building"() {
        when:
        def result = DataFetcherResult.newResult().data("hello")
                .error(error1).errors([error2]).localContext("world").build()
        then:
        result.getData() == "hello"
        result.getLocalContext() == "world"
        result.getErrors() == [error1, error2]
    }

    def "hasErrors can be called"() {
        when:
        def builder = DataFetcherResult.newResult()
        then:
        !builder.hasErrors()

        when:
        builder.error(error1)
        then:
        builder.hasErrors()

        when:
        def result = builder.build()
        then:
        result.hasErrors()
    }

    def "clearErrors can be called in a builder"() {
        when:
        def builder = DataFetcherResult.newResult()
                .errors([error1, error2])
        then:
        builder.hasErrors()

        when:
        builder.clearErrors()
        then:
        !builder.hasErrors()

        when:
        def result = builder.build()
        then:
        !result.hasErrors()
    }

    def "can set extensions"() {

        when:
        def dfr = DataFetcherResult.newResult()
                .extensions([x: "y"]).build()

        then:
        dfr.getExtensions() == [x : "y"]

        when:
        dfr = DataFetcherResult.newResult()
                .data("x")
                .build()

        then:
        dfr.getExtensions() == null

    }

    def "mapping works"() {
        when:
        def original = DataFetcherResult.newResult().data("hello")
                .errors([error1]).localContext("world")
                .extensions([x: "y"]).build()
        def result = original.map({ data -> data.length() })
        then:
        result.getData() == 5
        result.getLocalContext() == "world"
        result.getExtensions() == [x: "y"]
        result.getErrors() == [error1]
    }

    def "transforming works"() {
        when:
        def original = DataFetcherResult.newResult().data("hello")
                .errors([error1]).localContext("world")
                .extensions([x: "y"]).build()
        def result = original.transform({ builder -> builder.error(error2) })
        then:
        result.getData() == "hello"
        result.getLocalContext() == "world"
        result.getExtensions() == [x : "y"]
        result.getErrors() == [error1, error2]

        when:
        result = result.transform({ builder -> builder.extensions(a : "b") })
        then:
        result.getData() == "hello"
        result.getLocalContext() == "world"
        result.getExtensions() == [a : "b"]
        result.getErrors() == [error1, error2]
    }

    def "implements equals/hashCode for matching results"() {
        when:
        def firstResult = toDataFetcherResult(first)
        def secondResult = toDataFetcherResult(second)

        then:
        firstResult == secondResult
        firstResult.hashCode() == secondResult.hashCode()

        where:
        first                                                                                         | second
        [data: "A string"]                                                                            | [data: "A string"]
        [data: 5]                                                                                     | [data: 5]
        [data: ["a", "b"]]                                                                            | [data: ["a", "b"]]
        [errors: [error("An error")]]                                                                 | [errors: [error("An error")]]
        [data: "A value", errors: [error("An error")]]                                                | [data: "A value", errors: [error("An error")]]
        [data: "A value", localContext: 5]                                                            | [data: "A value", localContext: 5]
        [data: "A value", errors: [error("An error")], localContext: 5]                               | [data: "A value", errors: [error("An error")], localContext: 5]
        [data: "A value", extensions: ["key": "value"]]                                               | [data: "A value", extensions: ["key": "value"]]
        [data: "A value", errors: [error("An error")], localContext: 5, extensions: ["key": "value"]] | [data: "A value", errors: [error("An error")], localContext: 5, extensions: ["key": "value"]]
    }

    def "implements equals/hashCode for different results"() {
        when:
        def firstResult = toDataFetcherResult(first)
        def secondResult = toDataFetcherResult(second)

        then:
        firstResult != secondResult
        firstResult.hashCode() != secondResult.hashCode()

        where:
        first                                                                                         | second
        [data: "A string"]                                                                            | [data: "A different string"]
        [data: 5]                                                                                     | [data: "not 5"]
        [data: ["a", "b"]]                                                                            | [data: ["a", "c"]]
        [errors: [error("An error")]]                                                                 | [errors: [error("A different error")]]
        [data: "A value", errors: [error("An error")]]                                                | [data: "A different value", errors: [error("An error")]]
        [data: "A value", localContext: 5]                                                            | [data: "A value", localContext: 1]
        [data: "A value", errors: [error("An error")], localContext: 5]                               | [data: "A value", errors: [error("A different error")], localContext: 5]
        [data: "A value", extensions: ["key": "value"]]                                               | [data: "A value", extensions: ["key", "different value"]]
        [data: "A value", errors: [error("An error")], localContext: 5, extensions: ["key": "value"]] | [data: "A value", errors: [error("An error")], localContext: 5, extensions: ["key": "different value"]]
    }

    private static DataFetcherResult toDataFetcherResult(Map<String, Object> resultFields) {
        def resultBuilder = DataFetcherResult.newResult();
        resultFields.forEach { key, value ->
            if (value != null) {
                switch (key) {
                    case "data":
                        resultBuilder.data(value)
                        break;
                    case "errors":
                        resultBuilder.errors(value as List<GraphQLError>);
                        break;
                    case "localContext":
                        resultBuilder.localContext(value);
                        break;
                    case "extensions":
                        resultBuilder.extensions(value as Map<Object, Object>);
                        break;
                }
            }
        }
        return resultBuilder.build();
    }

    private static GraphQLError error(String message) {
        return GraphQLError.newError()
                .message(message)
                .build();
    }
}
