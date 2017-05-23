package graphql

import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

class ScalarsLongTest extends Specification {

    @Shared
    def tooBig = new BigInteger(Long.toString(Long.MAX_VALUE)).add(new BigInteger("1"))
    @Shared
    def tooSmall = new BigInteger(Long.toString(Long.MIN_VALUE)).subtract(new BigInteger("1"))

    @Unroll
    def "Long parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLLong.getCoercing().parseLiteral(literal) == result

        where:
        literal                                    | result
        new IntValue(42 as BigInteger)             | 42
        new IntValue(Long.MAX_VALUE as BigInteger) | Long.MAX_VALUE
        new IntValue(Long.MIN_VALUE as BigInteger) | Long.MIN_VALUE
        new StringValue("12345678910")             | 12345678910
        new StringValue("-1")                      | -1

    }

    @Unroll
    def "Long returns null for invalid #literal"() {
        expect:
        Scalars.GraphQLLong.getCoercing().parseLiteral(literal) == null

        where:
        literal                         | _
        new StringValue("not a number") | _
        new FloatValue(42.3)            | _
        tooBig                          | null
        tooSmall                        | null
        new FloatValue(42.3)            | null
    }

    @Unroll
    def "Long serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLLong.getCoercing().serialize(value) == result
        Scalars.GraphQLLong.getCoercing().parseValue(value) == result

        where:
        value                        | result
        "42"                         | 42
        "42.0000"                    | 42
        42.0000d                     | 42
        new Integer(42)              | 42
        "-1"                         | -1
        new BigInteger(42)           | 42
        new BigDecimal("42")         | 42
        42.0f                        | 42
        42.0d                        | 42
        new Byte("42")               | 42
        new Short("42")              | 42
        12345678910l                 | 12345678910l
        new AtomicInteger(42)        | 42
        Long.MAX_VALUE               | Long.MAX_VALUE
        Long.MIN_VALUE               | Long.MIN_VALUE
        new Long(42345784398534785l) | 42345784398534785l
    }

    @Unroll
    def "serialize/parseValue throws exception for invalid input #value"() {
        expect:
        try {
            Scalars.GraphQLLong.getCoercing().serialize(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }
        try {
            Scalars.GraphQLLong.getCoercing().parseValue(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }

        where:
        value            | _
        ""               | _
        "not a number "  | _
        "42.3"           | _
        new Double(42.3) | _
        new Float(42.3)  | _
        tooBig           | _
        tooSmall         | _
        new Object()     | _
    }


}
