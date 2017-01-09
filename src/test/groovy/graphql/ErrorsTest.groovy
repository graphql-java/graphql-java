package graphql

import graphql.language.SourceLocation
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification


@SuppressWarnings("ChangeToOperator")
class ErrorsTest extends Specification {

    def src(int line, int col) {
        new SourceLocation(line,col)
    }


    private static void commonAssert(GraphQLError same1, GraphQLError same2, GraphQLError different1) {
        same1.equals(same2)
        same1.hashCode() == same2.hashCode()

        same2.equals(same1)

        // we should equal ourselves
        same1.equals(same1)

        !same1.equals(different1)
        same1.hashCode() != different1.hashCode()
    }

    def "InvalidSyntaxError equals and hashcode works"() {
        expect:

        def same1 = new InvalidSyntaxError(src(15,34))
        def same2 = new InvalidSyntaxError(src(15,34))
        def different1 = new InvalidSyntaxError([src(24,100), src(15,34)])

        commonAssert(same1, same2, different1)
    }

    def "ValidationError equals and hashcode works"() {
        expect:

        def same1 = new ValidationError(ValidationErrorType.BadValueForDefaultArg,[src(15,34),src(23,567)],"bad ju ju")
        def same2 = new ValidationError(ValidationErrorType.BadValueForDefaultArg,[src(15,34),src(23,567)],"bad ju ju")
        def different1 = new ValidationError(ValidationErrorType.FieldsConflict,[src(15,34),src(23,567)],"bad ju ju")

        commonAssert(same1, same2, different1)
    }

    def "ExceptionWhileDataFetching equals and hashcode works"() {
        expect:

        def same1 = new ExceptionWhileDataFetching(new RuntimeException("bad ju ju"))
        def same2 = new ExceptionWhileDataFetching(new RuntimeException("bad ju ju"))
        def different1 = new ExceptionWhileDataFetching(new RuntimeException("unexpected ju ju"))

        commonAssert(same1, same2, different1)
    }



}
