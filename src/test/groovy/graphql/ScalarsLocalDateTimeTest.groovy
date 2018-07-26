package graphql

import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ScalarsLocalDateTimeTest extends Specification{

    @Unroll
    def "LocalDate parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLLocalDateTime.getCoercing().parseLiteral(literal) == result

        where:
        literal                       | result
        new StringValue("2014-11-01T22:13:48.87") | LocalDateTime.parse("2014-11-01T22:13:48.87", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        new StringValue("2015-12-01T21:22:33.87") | LocalDateTime.parse("2015-12-01T21:22:33.87", DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    }

    @Unroll
    def "LocalDate returns null for invalid #literal"() {
        when:
        Scalars.GraphQLLocalDateTime.getCoercing().parseLiteral(literal)
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
        Scalars.GraphQLLocalDateTime.getCoercing().serialize(value) == result
        Scalars.GraphQLLocalDateTime.getCoercing().parseValue(value) == result

        where:
        value | result
        LocalDateTime.parse("2014-11-01T22:13:48.87") | LocalDateTime.parse("2014-11-01T22:13:48.87")
        LocalDateTime.parse("2015-12-01T21:22:33.87") | LocalDateTime.parse("2015-12-01T21:22:33.87")
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
