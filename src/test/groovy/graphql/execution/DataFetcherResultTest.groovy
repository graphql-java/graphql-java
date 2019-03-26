package graphql.execution

import graphql.InvalidSyntaxError
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class DataFetcherResultTest extends Specification {

    def error1 = new ValidationError(ValidationErrorType.DuplicateOperationName)
    def error2 = new InvalidSyntaxError([], "Boo")

    def ext1Value = 42
    def ext2Value = "Fish fingers and Custard"

    def "basic building"() {
        when:
        def result = DataFetcherResult
                .newResult()
                .data("hello")
                .error(error1)
                .errors([error2])
                .extension("ext1", ext1Value)
                .extensions([11: ext2Value])
                .localContext("world")
                .build()
        then:
        result.getData() == "hello"
        result.getLocalContext() == "world"
        result.getErrors() == [error1, error2]
        result.getExtensions() == [ext1: ext1Value, 11: ext2Value]
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

    def "map relative is off by default"() {
        when:
        def result = DataFetcherResult.newResult().build()
        then:
        !result.isMapRelativeErrors()

        when:
        result = DataFetcherResult.newResult().mapRelativeErrors(true).build()
        then:
        result.isMapRelativeErrors()
    }

    def "extensions is null by default"() {
        when:
        def result = DataFetcherResult.newResult().build()
        then:
        result.getExtensions() == null
    }

    def "empty extensions results is null value when calling getter"() {
        when:
        def result = DataFetcherResult.newResult().extensions([:]).build()
        then:
        result.getExtensions() == null
    }
}
