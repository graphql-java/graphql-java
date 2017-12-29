package graphql

import graphql.execution.ExecutionPath
import graphql.execution.ExecutionTypeInfo
import graphql.execution.MissingRootTypeException
import graphql.execution.NonNullableFieldWasNullError
import graphql.execution.NonNullableFieldWasNullException
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
        new ValidationError(ValidationErrorType.UnknownType, mkLocations(), "Test ValidationError")    |
                [
                        locations: [[line: 666, column: 999], [line: 333, column: 0]],
                        message  : "Validation error of type UnknownType: Test ValidationError",
                ]

        new MissingRootTypeException("Mutations are not supported on this schema", null)               |
                [
                        message: "Mutations are not supported on this schema",
                ]

        new InvalidSyntaxError(mkLocations(), "Not good syntax m'kay")                                 |
                [
                        locations: [[line: 666, column: 999], [line: 333, column: 0]],
                        message  : "Invalid Syntax : Not good syntax m'kay",
                ]

        new NonNullableFieldWasNullError(new NonNullableFieldWasNullException(mkTypeInfo(), mkPath())) |
                [
                        message: 'Cannot return null for non-nullable type: \'__Schema\' (/heroes[0]/abilities/speed[4])',
                        path   : ["heroes", 0, "abilities", "speed", 4],
                ]

        new SerializationError(mkPath(), new CoercingSerializeException("Bad coercing"))               |
                [
                        message: "Can't serialize value (/heroes[0]/abilities/speed[4]) : Bad coercing",
                        path   : ["heroes", 0, "abilities", "speed", 4],
                ]

        new ExceptionWhileDataFetching(mkPath(), new RuntimeException("Bang"), mkLocation(666, 999))   |
                [locations: [[line: 666, column: 999]],
                 message  : "Exception while fetching data (/heroes[0]/abilities/speed[4]) : Bang",
                 path     : ["heroes", 0, "abilities", "speed", 4],
                ]

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
            return null
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
        return [mkLocation(666, 999), mkLocation(333, 0)]
    }

    SourceLocation mkLocation(int line, int column) {
        return new SourceLocation(line, column)
    }

    ExecutionPath mkPath() {
        return ExecutionPath.rootPath()
                .segment("heroes")
                .segment(0)
                .segment("abilities")
                .segment("speed")
                .segment(4)
    }

    ExecutionTypeInfo mkTypeInfo() {
        return ExecutionTypeInfo.newTypeInfo()
                .type(Introspection.__Schema)
                .path(mkPath())
                .build()
    }
}
