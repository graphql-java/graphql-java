package graphql

import graphql.execution.ResultPath
import graphql.language.SourceLocation
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification


@SuppressWarnings("ChangeToOperator")
class ErrorsTest extends Specification {

    def src(int line, int col) {
        new SourceLocation(line, col)
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

        def msg = "problems at the ok corral"

        def same1 = new InvalidSyntaxError(src(15, 34), msg)
        def same2 = new InvalidSyntaxError(src(15, 34), msg)
        def different1 = new InvalidSyntaxError([src(24, 100), src(15, 34)], msg)

        commonAssert(same1, same2, different1)
    }

    def "ValidationError equals and hashcode works"() {
        expect:

        def same1 = ValidationError.newValidationError()
                .validationErrorType(ValidationErrorType.BadValueForDefaultArg)
                .sourceLocations([src(15, 34), src(23, 567)])
                .description("bad ju ju")
                .build()
        def same2 = ValidationError.newValidationError()
                .validationErrorType(ValidationErrorType.BadValueForDefaultArg)
                .sourceLocations([src(15, 34), src(23, 567)])
                .description("bad ju ju")
                .build()
        def different1 = ValidationError.newValidationError()
                .validationErrorType(ValidationErrorType.FieldsConflict)
                .sourceLocations([src(15, 34), src(23, 567)])
                .description("bad ju ju")
                .build()

        commonAssert(same1, same2, different1)
    }

    def "ExceptionWhileDataFetching equals and hashcode works"() {
        expect:

        def same1 = new ExceptionWhileDataFetching(ResultPath.rootPath(), new RuntimeException("bad ju ju"), null)
        def same2 = new ExceptionWhileDataFetching(ResultPath.rootPath(), new RuntimeException("bad ju ju"), null)
        def different1 = new ExceptionWhileDataFetching(ResultPath.rootPath(), new RuntimeException("unexpected ju ju"), null)

        commonAssert(same1, same2, different1)
    }


}
