package graphql

import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate


class ScalarsLocalDateTest extends Specification {

    @Unroll
    def "LocalDate parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLLocalDate.getCoercing().parseLiteral(literal) == result

        where:
        literal                              | result
        new StringValue("2099-09-09") | LocalDate.parse("2099-09-09")
        new StringValue("2011-11-11") | LocalDate.parse("2011-11-11")

    }

    @Unroll
    def "LocalDate returns null for invalid #literal"() {
        when:
        Scalars.GraphQLLocalDate.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                        | _
        new String("notvalid")          | _
        new String("not-valid") | _
    }

    @Unroll
    def "LocalDate serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLLocalDate.getCoercing().serialize(value) == result
        Scalars.GraphQLLocalDate.getCoercing().parseValue(value) == result

        where:
        value | result
        LocalDate.parse("2099-09-09")  | LocalDate.parse("2099-09-09")
        LocalDate.parse("2099-09-09")  | LocalDate.parse("2099-09-09")
    }

    @Unroll
    def "serialize throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLLocalDate.getCoercing().serialize(value)
        then:
        thrown(CoercingSerializeException)

        where:
        value        | _
        ""           | _
        "aa"         | _
        new Object() | _

    }

    @Unroll
    def "parseValue throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLLocalDate.getCoercing().parseValue(value)
        then:
        thrown(CoercingParseValueException)

        where:
        value        | _
        ""           | _
        "aa"         | _
        new Object() | _

    }

}
