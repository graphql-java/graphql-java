package graphql

import graphql.language.SourceLocation
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class GraphqlErrorHelperTest extends Specification {

    class TestError implements GraphQLError {
        @Override
        String getMessage() {
            return "test"
        }

        @Override
        List<SourceLocation> getLocations() {
            return [new SourceLocation(6, 9)]
        }

        @Override
        ErrorClassification getErrorType() {
            return new ErrorClassification() {
                @Override
                Object toSpecification(GraphQLError error) {
                    return [statusCode: 200, reason: "Bad juj ju"]
                }
            }
        }

        @Override
        Map<String, Object> getExtensions() {
            return [extra: "extensionData"]
        }
    }

    class ExtensionAddingError implements GraphQLError {

        Map<String, Object> extensions

        ExtensionAddingError(Map<String, Object> extensions) {
            this.extensions = extensions
        }

        @Override
        String getMessage() {
            return "has extensions"
        }

        @Override
        List<SourceLocation> getLocations() {
            return null
        }

        @Override
        ErrorClassification getErrorType() {
            return null
        }

        @Override
        Map<String, Object> getExtensions() {
            return extensions
        }
    }

    def "can turn error classifications into extensions"() {

        def validationErr = ValidationError.newValidationError()
                .validationErrorType(ValidationErrorType.InvalidFragmentType)
                .sourceLocation(new SourceLocation(6, 9))
                .description("Things are not valid")
                .build()

        when:
        def specMap = GraphqlErrorHelper.toSpecification(validationErr)
        then:
        specMap == [
                locations : [[line: 6, column: 9]],
                message   : "Things are not valid",
                extensions: [classification: "ValidationError"],

        ]
    }

    def "can handle custom extensions and custom error classification"() {
        when:
        def specMap = GraphqlErrorHelper.toSpecification(new TestError())
        then:
        specMap == [extensions: [
                extra         : "extensionData",
                classification: [
                        statusCode: 200,
                        reason    : "Bad juj ju"
                ]],
                    locations : [[line: 6, column: 9]],
                    message   : "test"
        ]
    }

    def "can handle custom extensions with classification"() {
        when:
        def specMap = GraphqlErrorHelper.toSpecification(new ExtensionAddingError([classification: "help"]))
        then:
        specMap == [extensions: [
                classification: "help"],
                    message   : "has extensions"
        ]
    }
}
