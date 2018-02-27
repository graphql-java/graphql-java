package graphql

import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

class ScalarsBigDecimalTest extends Specification {

    @Unroll
    def "BigDecimal parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLBigDecimal.getCoercing().parseLiteral(literal) == result

        where:
        literal                                 | result
        new IntValue(12345678910 as BigInteger) | new BigDecimal("12345678910")
        new StringValue("12345678911.12")       | new BigDecimal("12345678911.12")
        new FloatValue(new BigDecimal("42.42")) | new BigDecimal("42.42")

    }

    @Unroll
    def "BigDecimal returns null for invalid #literal"() {
        when:
        Scalars.GraphQLBigDecimal.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                         | _
        new BooleanValue(true)          | _
        new StringValue("not a number") | _
    }

    @Unroll
    def "BigDecimal serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLBigDecimal.getCoercing().serialize(value) == result
        Scalars.GraphQLBigDecimal.getCoercing().parseValue(value) == result

        where:
        value                 | result
        "42"                  | new BigDecimal("42")
        "42.123"              | new BigDecimal("42.123")
        42.0000d              | new BigDecimal("42.000")
        new Integer(42)       | new BigDecimal("42")
        "-1"                  | new BigDecimal("-1")
        new BigInteger(42)    | new BigDecimal("42")
        new BigDecimal("42")  | new BigDecimal("42")
        42.3f                 | new BigDecimal("42.3")
        42.0d                 | new BigDecimal("42")
        new Byte("42")        | new BigDecimal("42")
        new Short("42")       | new BigDecimal("42")
        1234567l              | new BigDecimal("1234567")
        new AtomicInteger(42) | new BigDecimal("42")
    }

    @Unroll
    def "serialize throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLBigDecimal.getCoercing().serialize(value)
        then:
        thrown(CoercingSerializeException)

        where:
        value           | _
        ""              | _
        "not a number " | _
        new Object()    | _
    }

    @Unroll
    def "parseValue throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLBigDecimal.getCoercing().parseValue(value)
        then:
        thrown(CoercingParseValueException)

        where:
        value           | _
        ""              | _
        "not a number " | _
        new Object()    | _
    }

}
