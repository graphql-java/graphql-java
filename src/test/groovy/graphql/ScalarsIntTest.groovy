package graphql

import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

class ScalarsIntTest extends Specification {

    @Unroll
    def "Int parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLInt.getCoercing().parseLiteral(literal) == result

        where:
        literal                                       | result
        new IntValue(42 as BigInteger)                | 42
        new IntValue(Integer.MAX_VALUE as BigInteger) | Integer.MAX_VALUE
        new IntValue(Integer.MIN_VALUE as BigInteger) | Integer.MIN_VALUE

    }

    @Unroll
    def "Int returns null for invalid #literal"() {
        when:
        Scalars.GraphQLInt.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                                            | _
        new IntValue(12345678910 as BigInteger)            | _
        new StringValue("-1")                              | _
        new FloatValue(42.3)                               | _
        new IntValue(Integer.MAX_VALUE + 1l as BigInteger) | _
        new IntValue(Integer.MIN_VALUE - 1l as BigInteger) | _
        new StringValue("-1")                              | _
        new FloatValue(42.3)                               | _

    }

    @Unroll
    def "Int serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLInt.getCoercing().serialize(value) == result
        Scalars.GraphQLInt.getCoercing().parseValue(value) == result

        where:
        value                 | result
        "42"                  | 42
        "42.0000"             | 42
        42.0000d              | 42
        new Integer(42)       | 42
        "-1"                  | -1
        new BigInteger(42)    | 42
        new BigDecimal("42")  | 42
        42.0f                 | 42
        42.0d                 | 42
        new Byte("42")        | 42
        new Short("42")       | 42
        1234567l              | 1234567
        new AtomicInteger(42) | 42
        Integer.MAX_VALUE     | Integer.MAX_VALUE
        Integer.MIN_VALUE     | Integer.MIN_VALUE
    }

    @Unroll
    def "serialize throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLInt.getCoercing().serialize(value)
        then:
        thrown(CoercingSerializeException)

        where:
        value                        | _
        ""                           | _
        "not a number "              | _
        "42.3"                       | _
        new Long(42345784398534785l) | _
        new Double(42.3)             | _
        new Float(42.3)              | _
        Integer.MAX_VALUE + 1l       | _
        Integer.MIN_VALUE - 1l       | _
        new Object()                 | _

    }

    @Unroll
    def "parsValue throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLInt.getCoercing().parseValue(value)
        then:
        thrown(CoercingParseValueException)


        where:
        value                        | _
        ""                           | _
        "not a number "              | _
        "42.3"                       | _
        new Long(42345784398534785l) | _
        new Double(42.3)             | _
        new Float(42.3)              | _
        Integer.MAX_VALUE + 1l       | _
        Integer.MIN_VALUE - 1l       | _
        new Object()                 | _

    }

}
