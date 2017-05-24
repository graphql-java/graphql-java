package graphql

import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

class ScalarsBigIntegerTest extends Specification {

    @Unroll
    def "BigInteger parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLBigInteger.getCoercing().parseLiteral(literal) == result

        where:
        literal                                 | result
        new IntValue(12345678910 as BigInteger) | new BigInteger("12345678910")
        new StringValue("12345678911")          | new BigInteger("12345678911")
        new FloatValue(new BigDecimal("42"))    | new BigInteger("42")
    }

    @Unroll
    def "BigInteger returns null for invalid #literal"() {
        expect:
        Scalars.GraphQLBigInteger.getCoercing().parseLiteral(literal) == null

        where:
        literal                                 | _
        new BooleanValue(true)                  | _
        new StringValue("42.3")                 | _
        new FloatValue(new BigDecimal("12.12")) | _
        new StringValue("not a number")         | _
    }

    @Unroll
    def "BigInteger serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLBigInteger.getCoercing().serialize(value) == result
        Scalars.GraphQLBigInteger.getCoercing().parseValue(value) == result

        where:
        value                 | result
        "42"                  | new BigInteger("42")
        new Integer(42)       | new BigInteger("42")
        "-1"                  | new BigInteger("-1")
        new BigInteger("42")  | new BigInteger("42")
        42.0d                 | new BigInteger("42")
        new Byte("42")        | new BigInteger("42")
        new Short("42")       | new BigInteger("42")
        1234567l              | new BigInteger("1234567")
        new AtomicInteger(42) | new BigInteger("42")
    }

    @Unroll
    def "serialize/parseValue throws exception for invalid input #value"() {
        expect:
        try {
            Scalars.GraphQLBigInteger.getCoercing().serialize(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }
        try {
            Scalars.GraphQLBigInteger.getCoercing().parseValue(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }

        where:
        value                   | _
        ""                      | _
        "not a number "         | _
        new BigDecimal("12.12") | _
        "12.12"                 | _
        new Object()            | _
    }


}
