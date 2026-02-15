package graphql

import graphql.execution.ExecutionStepInfo
import graphql.execution.MissingRootTypeException
import graphql.execution.NonNullableFieldWasNullError
import graphql.execution.NonNullableFieldWasNullException
import graphql.execution.ResultPath
import graphql.introspection.Introspection
import graphql.language.SourceLocation
import graphql.schema.CoercingSerializeException
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification
import spock.lang.Unroll

class GraphQLErrorTest extends Specification {

    @Unroll
    def "toSpecification works as expected for #gError.class"() {

        expect:

        gError.toSpecification() == expectedMap

        where:

        gError                                                                                         | expectedMap
        ValidationError.newValidationError()
                .validationErrorType(ValidationErrorType.UnknownType)
                .sourceLocations(mkLocations())
                .description("Test ValidationError")
                .build()                                                                               |
                [
                        locations: [[line: 666, column: 999], [line: 333, column: 1]],
                        message  : "Test ValidationError",
                        extensions:[classification:"ValidationError"],
                ]

        new MissingRootTypeException("Mutations are not supported on this schema", null)               |
                [
                        message: "Mutations are not supported on this schema",
                        extensions:[classification:"OperationNotSupported"],
                ]

        new InvalidSyntaxError(mkLocations(), "Not good syntax m'kay")                                 |
                [
                        locations: [[line: 666, column: 999], [line: 333, column: 1]],
                        message  : "Not good syntax m'kay",
                        extensions:[classification:"InvalidSyntax"],
                ]

        new NonNullableFieldWasNullError(new NonNullableFieldWasNullException(mkTypeInfo(), mkPath())) |
                [
                        message: '''The field at path '/heroes[0]/abilities/speed[4]' was declared as a non null type, but the code involved in retrieving data has wrongly returned a null value.  The graphql specification requires that the parent field be set to null, or if that is non nullable that it bubble up null to its parent and so on. The non-nullable type is '__Schema\'''',
                        path   : ["heroes", 0, "abilities", "speed", 4],
                        extensions:[classification:"NullValueInNonNullableField"],
                ]

        new SerializationError(mkPath(), new CoercingSerializeException("Bad coercing"))               |
                [
                        message: "Can't serialize value (/heroes[0]/abilities/speed[4]) : Bad coercing",
                        path   : ["heroes", 0, "abilities", "speed", 4],
                        extensions:[classification:"DataFetchingException"],
                ]

        new ExceptionWhileDataFetching(mkPath(), new RuntimeException("Bang"), mkLocation(666, 999))   |
                [locations: [[line: 666, column: 999]],
                 message  : "Exception while fetching data (/heroes[0]/abilities/speed[4]) : Bang",
                 path     : ["heroes", 0, "abilities", "speed", 4],
                 extensions:[classification:"DataFetchingException"],
                ]

    }

    def "toSpecification filters out error locations with line and column not starting at 1, as required in spec"() {
        // See specification wording: https://spec.graphql.org/draft/#sec-Errors.Error-Result-Format

        given:
        def error = ValidationError.newValidationError()
                .validationErrorType(ValidationErrorType.UnknownType)
                .sourceLocations([mkLocation(-1, -1), mkLocation(333, 1)])
                .description("Test ValidationError")
                .build()

        def expectedMap = [
                locations: [
                        [line: 333, column: 1]
                ],
                message: "Test ValidationError",
                extensions: [classification:"ValidationError"]
        ]

        expect:
        error.toSpecification() == expectedMap
    }

    def "toSpecification filters out null error locations"() {
        given:
        def error = ValidationError.newValidationError()
                .validationErrorType(ValidationErrorType.UnknownType)
                .sourceLocations([null, mkLocation(333, 1)])
                .description("Test ValidationError")
                .build()

        def expectedMap = [
                locations: [
                        [line: 333, column: 1]
                ],
                message: "Test ValidationError",
                extensions: [classification:"ValidationError"]
        ]

        expect:
        error.toSpecification() == expectedMap
    }

    class CustomException extends RuntimeException implements GraphQLError {
        private LinkedHashMap<String, String> map

        @Override
        Map<Object, Object> getExtensions() {
            return map
        }

        @Override
        List<SourceLocation> getLocations() {
            return null
        }

        @Override
        ErrorType getErrorType() {
            return ErrorType.DataFetchingException
        }
    }

    @Unroll
    def "transfer of extensions values works on ErrorWhileDataFetching case: #testCase"() {

        given:
        def fetching = new ExceptionWhileDataFetching(mkPath(), exceptionInstance, mkLocation(1, 2))

        expect:
        fetching.getExtensions() == expectedMap

        where:

        testCase     | exceptionInstance                                      | expectedMap
        'has map'    | new CustomException(map: [key1: "val1", key2: "val2"]) | [key1: "val1", key2: "val2"]
        'empty map'  | new CustomException(map: [:])                          | [:]
        'null map'   | new CustomException(map: null)                         | null
        'not gError' | new RuntimeException("No extensions")                  | null
    }

    List<SourceLocation> mkLocations() {
        return [mkLocation(666, 999), mkLocation(333, 1)]
    }

    SourceLocation mkLocation(int line, int column) {
        return new SourceLocation(line, column)
    }

    ResultPath mkPath() {
        return ResultPath.rootPath()
                .segment("heroes")
                .segment(0)
                .segment("abilities")
                .segment("speed")
                .segment(4)
    }

    ExecutionStepInfo mkTypeInfo() {
        return ExecutionStepInfo.newExecutionStepInfo()
                .type(Introspection.__Schema)
                .path(mkPath())
                .build()
    }
}
