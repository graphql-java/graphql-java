package graphql

import graphql.language.BooleanValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsStringTest extends Specification {


    @Shared
    def customObject = new Object() {
        @Override
        String toString() {
            return "foo"
        }
    }

    @Unroll
    def "String parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal) == result

        where:
        literal                   | result
        new StringValue("1234ab") | "1234ab"

    }

    @Unroll
    def "String returns null for invalid #literal"() {
        when:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                | _
        new BooleanValue(true) | _
    }

    @Unroll
    def "String serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLString.getCoercing().serialize(value) == result
        Scalars.GraphQLString.getCoercing().parseValue(value) == result

        where:
        value        | result
        true         | "true"
        false        | "false"
        "123ab"      | "123ab"
        123          | "123"
    }

    @Unroll
    def "String serialize complex #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLString.getCoercing().serialize(value) == result

        where:
        value        | result
        customObject | "foo"
    }

    @Unroll
    def "String parseValue throws exception for complex input #value"() {
        when:
        Scalars.GraphQLString.getCoercing().parseValue(value)
        then:
        thrown(CoercingParseValueException)

        where:
        value        | _
        customObject | _
        new Object() | _
        [:]          | _
        []           | _
    }

}
