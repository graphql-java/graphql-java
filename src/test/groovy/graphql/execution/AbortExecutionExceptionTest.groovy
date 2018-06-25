package graphql.execution

import graphql.ErrorType
import graphql.GraphQLError
import graphql.language.SourceLocation
import spock.lang.Specification

class AbortExecutionExceptionTest extends Specification {

    class BasicError implements GraphQLError {
        def message

        @Override
        String getMessage() {
            return message
        }

        @Override
        List<SourceLocation> getLocations() {
            return null
        }

        @Override
        ErrorType getErrorType() {
            return null
        }
    }

    def "to excution result handling"() {
        AbortExecutionException e
        when:
        e = new AbortExecutionException("No underlying errors")
        then:
        e.toExecutionResult().getErrors().size() == 1
        e.toExecutionResult().getErrors()[0].message == "No underlying errors"

        when:
        e = new AbortExecutionException([new BasicError(message:"UnderlyingA"), new BasicError(message:"UnderlyingB")])
        then:
        e.toExecutionResult().getErrors().size() == 2
        e.toExecutionResult().getErrors()[0].message == "UnderlyingA"
        e.toExecutionResult().getErrors()[1].message == "UnderlyingB"
    }
}
