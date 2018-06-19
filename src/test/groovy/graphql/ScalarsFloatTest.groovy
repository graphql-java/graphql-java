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

class ScalarsFloatTest extends Specification {

    @Unroll
    def "Float parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().parseLiteral(literal) == result

        where:
        literal                                 | result
        new FloatValue(42.442 as BigDecimal)    | 42.442
        new FloatValue(Double.MAX_VALUE)        | Double.MAX_VALUE
        new FloatValue(Double.MIN_VALUE)        | Double.MIN_VALUE
        new IntValue(12345678910 as BigInteger) | 12345678910

    }

    @Unroll
    def "Float returns null for invalid #literal"() {
        when:
        Scalars.GraphQLFloat.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                | _
        new BooleanValue(true) | _
        new StringValue("")    | _
    }

    @Unroll
    def "Float serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().serialize(value) == result
        Scalars.GraphQLFloat.getCoercing().parseValue(value) == result

        where:
        value                 | result
        "42"                  | 42d
        "42.123"              | 42.123d
        42.0000d              | 42
        new Integer(42)       | 42
        "-1"                  | -1
        new BigInteger(42)    | 42
        new BigDecimal("42")  | 42
        42.3f                 | 42.3d
        42.0d                 | 42d
        new Byte("42")        | 42
        new Short("42")       | 42
        1234567l              | 1234567d
        new AtomicInteger(42) | 42
        Double.MAX_VALUE      | Double.MAX_VALUE
        Double.MIN_VALUE      | Double.MIN_VALUE
    }

    @Unroll
    def "serialize throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLFloat.getCoercing().serialize(value)
        then:
        thrown(CoercingSerializeException)

        where:
        value           | _
        ""              | _
        "not a number " | _
        Double.NaN      | _
    }

    @Unroll
    def "serialize/parseValue throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLFloat.getCoercing().parseValue(value)
        then:
        thrown(CoercingParseValueException)

        where:
        value           | _
        ""              | _
        "not a number " | _
        Double.NaN      | _
    }

}
