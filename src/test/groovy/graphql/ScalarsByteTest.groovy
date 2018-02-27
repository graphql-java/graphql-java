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

class ScalarsByteTest extends Specification {

    @Unroll
    def "Byte parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLByte.getCoercing().parseLiteral(literal) == result

        where:
        literal                                    | result
        new IntValue(42 as BigInteger)             | 42
        new IntValue(Byte.MAX_VALUE as BigInteger) | Byte.MAX_VALUE
        new IntValue(Byte.MIN_VALUE as BigInteger) | Byte.MIN_VALUE

    }

    @Unroll
    def "Byte returns null for invalid #literal"() {
        when:
        Scalars.GraphQLByte.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                                         | _
        new IntValue(12345678910 as BigInteger)         | _
        new StringValue("-1")                           | _
        new FloatValue(42.3)                            | _
        new IntValue(Byte.MAX_VALUE + 1l as BigInteger) | _
        new IntValue(Byte.MIN_VALUE - 1l as BigInteger) | _
        new StringValue("-1")                           | _
        new FloatValue(42.3)                            | _

    }

    @Unroll
    def "Byte serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLByte.getCoercing().serialize(value) == result
        Scalars.GraphQLByte.getCoercing().parseValue(value) == result

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
        123l                  | 123
        new AtomicInteger(42) | 42
        Byte.MAX_VALUE        | Byte.MAX_VALUE
        Byte.MIN_VALUE        | Byte.MIN_VALUE
    }

    @Unroll
    def "serialize throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLByte.getCoercing().serialize(value)
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
        Byte.MAX_VALUE + 1l          | _
        Byte.MIN_VALUE - 1l          | _
        new Object()                 | _

    }

    @Unroll
    def "parseValue throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLByte.getCoercing().parseValue(value)
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
        Byte.MAX_VALUE + 1l          | _
        Byte.MIN_VALUE - 1l          | _
        new Object()                 | _

    }

}
