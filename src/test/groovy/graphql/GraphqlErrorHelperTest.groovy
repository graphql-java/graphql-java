package graphql

import graphql.language.SourceLocation
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.RepeatUntilFailure
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
            return ErrorType.DataFetchingException
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

    def "can parse out a map and make an error"() {
        when:
        def rawError = [message: "m"]
        def graphQLError = GraphqlErrorHelper.fromSpecification(rawError)
        then:
        graphQLError.getMessage() == "m"
        graphQLError.getErrorType() == ErrorType.DataFetchingException // default from error builder
        graphQLError.getLocations() == []
        graphQLError.getPath() == null
        graphQLError.getExtensions() == null

        when:
        rawError = [message: "m"]
        graphQLError = GraphQLError.fromSpecification(rawError) // just so we reference the public method
        then:
        graphQLError.getMessage() == "m"
        graphQLError.getErrorType() == ErrorType.DataFetchingException // default from error builder
        graphQLError.getLocations() == []
        graphQLError.getPath() == null
        graphQLError.getExtensions() == null

        when:
        def extensionsMap = [attr1: "a1", attr2: "a2", classification: "CLASSIFICATION-X"]
        rawError = [message: "m", path: ["a", "b"], locations: [[line: 2, column: 3]], extensions: extensionsMap]
        graphQLError = GraphqlErrorHelper.fromSpecification(rawError)

        then:
        graphQLError.getMessage() == "m"
        graphQLError.getErrorType().toString() == "CLASSIFICATION-X"
        graphQLError.getLocations() == [new SourceLocation(2, 3)]
        graphQLError.getPath() == ["a", "b"]
        graphQLError.getExtensions() == extensionsMap


        when: "can do a list of errors"
        def rawErrors = [[message: "m0"], [message: "m1"]]
        def errors = GraphqlErrorHelper.fromSpecification(rawErrors)
        then:
        errors.size() == 2
        errors.eachWithIndex { GraphQLError gErr, int i ->
            assert gErr.getMessage() == "m" + i
            assert gErr.getErrorType() == ErrorType.DataFetchingException // default from error builder
            assert gErr.getLocations() == []
            assert gErr.getPath() == null
            assert gErr.getExtensions() == null
        }
    }

    @RepeatUntilFailure(maxAttempts = 1_000, ignoreRest = false)
    def "can deterministically serialize SourceLocation"() {
        when:
        def specMap = GraphqlErrorHelper.toSpecification(new TestError())

        then:
        def location = specMap["locations"][0] as Map<String, Object>
        def keys = location.keySet().toList()
        keys == ["line", "column"]
    }
}
