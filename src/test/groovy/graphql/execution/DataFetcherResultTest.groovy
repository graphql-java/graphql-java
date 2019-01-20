package graphql.execution

import graphql.InvalidSyntaxError
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class DataFetcherResultTest extends Specification {

    def "basic building"() {
        def error1 = new ValidationError(ValidationErrorType.DuplicateOperationName)
        def error2 = new InvalidSyntaxError([], "Boo")
        when:
        def result = DataFetcherResult.newResult().data("hello")
                .error(error1).errors([error2]).localContext("world").build()
        then:
        result.getData() == "hello"
        result.getLocalContext() == "world"
        result.getErrors() == [error1, error2]
    }
}
