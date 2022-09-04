package graphql

import graphql.execution.CoercedVariables
import graphql.language.BooleanValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
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
        Scalars.GraphQLString.getCoercing().parseLiteral(literal, CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default) == result

        where:
        literal                   | result
        new StringValue("1234ab") | "1234ab"
    }

    @Unroll
    def "String parse literal #literal.value as #result with deprecated method"() {
        expect:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal) == result // Retain deprecated for test coverage

        where:
        literal                   | result
        new StringValue("1234ab") | "1234ab"
    }

    @Unroll
    def "String returns null for invalid #literal"() {
        when:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal, CoercedVariables.emptyVariables(), GraphQLContext.default, Locale.default)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                | _
        new BooleanValue(true) | _
    }

    @Unroll
    def "String serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLString.getCoercing().serialize(value, GraphQLContext.default, Locale.default) == result
        Scalars.GraphQLString.getCoercing().parseValue(value, GraphQLContext.default, Locale.default) == result

        where:
        value        | result
        "123ab"      | "123ab"
        123          | "123"
        customObject | "foo"
    }

    @Unroll
    def "String serialize #value into #result (#result.class) with deprecated methods"() {
        expect:
        Scalars.GraphQLString.getCoercing().serialize(value) == result // Retain deprecated method for test coverage
        Scalars.GraphQLString.getCoercing().parseValue(value) == result // Retain deprecated method for test coverage

        where:
        value        | result
        "123ab"      | "123ab"
        123          | "123"
        customObject | "foo"
    }

}
