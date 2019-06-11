package graphql

import graphql.language.BooleanValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsIDTest extends Specification {

    @Unroll
    def "ID parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLID.getCoercing().parseLiteral(literal) == result

        where:
        literal                         | result
        new StringValue("1234ab")       | "1234ab"
        new IntValue(123 as BigInteger) | "123"

    }

    @Unroll
    def "ID returns null for invalid #literal"() {
        when:
        Scalars.GraphQLID.getCoercing().parseLiteral(literal)
        then:
        thrown(CoercingParseLiteralException)

        where:
        literal                | _
        new BooleanValue(true) | _
    }

    @Unroll
    def "ID serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLID.getCoercing().serialize(value) == result
        Scalars.GraphQLID.getCoercing().parseValue(value) == result

        where:
        value               | result
        "123ab"             | "123ab"
        123                 | "123"
        123123123123123123L | "123123123123123123"
        UUID.fromString("037ebc7a-f9b8-4d76-89f6-31b34a40e10b") | "037ebc7a-f9b8-4d76-89f6-31b34a40e10b"
    }

    @Unroll
    def "serialize throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLID.getCoercing().serialize(value)
        then:
        thrown(CoercingSerializeException)

        where:
        value        | _
        new Object() | _

    }

    @Unroll
    def "parseValue throws exception for invalid input #value"() {
        when:
        Scalars.GraphQLID.getCoercing().parseValue(value)
        then:
        thrown(CoercingParseValueException)

        where:
        value        | _
        new Object() | _

    }


}
