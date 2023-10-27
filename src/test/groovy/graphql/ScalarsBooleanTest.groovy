package graphql

import graphql.execution.CoercedVariables
import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsBooleanTest extends Specification {

    @Unroll
    def "Boolean parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseLiteral(literal, CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == result

        where:
        literal                 | result
        new BooleanValue(true)  | true
        new BooleanValue(false) | false
    }

    @Unroll
    def "Boolean parse literal #literal.value as #result with deprecated method"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseLiteral(literal) == result // Retain deprecated method for test coverage

        where:
        literal                 | result
        new BooleanValue(true)  | true
        new BooleanValue(false) | false
    }

    @Unroll
    def "Boolean returns null for invalid #literal"() {
        when:
        Scalars.GraphQLBoolean.getCoercing().parseLiteral(literal, CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                                 | _
        new IntValue(12345678910 as BigInteger) | _
        new StringValue("-1")                   | _
        new FloatValue(42.3)                    | _
    }

    @Unroll
    def "Boolean serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().serialize(value, GraphQLContext.default, Locale.default) == result

        where:
        value                        | result
        true                         | true
        "false"                      | false
        "true"                       | true
        "True"                       | true
        0                            | false
        1                            | true
        -1                           | true
        new Long(42345784398534785l) | true
        new Double(42.3)             | true
        new Float(42.3)              | true
        Integer.MAX_VALUE + 1l       | true
        Integer.MIN_VALUE - 1l       | true
    }

    @Unroll
    def "Boolean serialize #value into #result (#result.class) with deprecated methods"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().serialize(value) == result // Retain deprecated method for test coverage

        where:
        value                        | result
        true                         | true
        "false"                      | false
        "true"                       | true
        "True"                       | true
        0                            | false
        1                            | true
        -1                           | true
        new Long(42345784398534785l) | true
        new Double(42.3)             | true
        new Float(42.3)              | true
        Integer.MAX_VALUE + 1l       | true
        Integer.MIN_VALUE - 1l       | true
    }

    @Unroll
    def "serialize throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLBoolean.getCoercing().serialize(value, GraphQLContext.default, Locale.default)
        then:
        thrown(CoercingSerializeException)

        where:
        value                 | _
        new Object()          | _
        "some value not true" | _
        ""                    | _
        "T"                   | _
        "t"                   | _
        "F"                   | _
        "f"                   | _
    }

    @Unroll
    def "Boolean parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseValue(value, GraphQLContext.default, Locale.default) == result

        where:
        value | result
        true  | true
        false | false
    }

    @Unroll
    def "Boolean parseValue #value into #result (#result.class) with deprecated methods"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseValue(value) == result // Retain deprecated method for test coverage

        where:
        value | result
        true  | true
        false | false
    }

    @Unroll
    def "parseValue parses non-Boolean input #value"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseValue(value, GraphQLContext.default, Locale.default) == result

        where:
        value                        | result
        true                         | true
        "false"                      | false
        "true"                       | true
        "True"                       | true
        0                            | false
        1                            | true
        -1                           | true
        new Long(42345784398534785l) | true
        new Double(42.3)             | true
        new Float(42.3)              | true
        Integer.MAX_VALUE + 1l       | true
        Integer.MIN_VALUE - 1l       | true
    }

    @Unroll
    def "parseValue throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLBoolean.getCoercing().parseValue(value, GraphQLContext.default, Locale.default)
        then:
        thrown(CoercingParseValueException)

        where:
        value                        | _
        new Object()                 | _
    }

}
