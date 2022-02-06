package graphql.execution

import graphql.InvalidSyntaxError
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class DataFetcherResultTest extends Specification {

    def error1 = new ValidationError(ValidationErrorType.DuplicateOperationName)
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

    def "transforming"() {
        when:
        def original = DataFetcherResult.newResult().data("hello")
                .errors([error1]).localContext("world").build()
        def result = original.transform({ builder -> builder.error(error2) })
        then:
        result.getData() == "hello"
        result.getLocalContext() == "world"
        result.getErrors() == [error1, error2]
    }
}
