package graphql

import graphql.language.BooleanValue
import graphql.language.IntValue
import graphql.language.StringValue
import graphql.relay.DefaultConnectionCursor
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
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
        value                                                   | result
        "123ab"                                                 | "123ab"
        123                                                     | "123"
        123123123123123123L                                     | "123123123123123123"
        new BigInteger("123123123123123123")                    | "123123123123123123"
        UUID.fromString("037ebc7a-f9b8-4d76-89f6-31b34a40e10b") | "037ebc7a-f9b8-4d76-89f6-31b34a40e10b"
        new DefaultConnectionCursor("cursor123")                | "cursor123"
    }

    @Unroll
    def "serialize allows any object via String.valueOf #value"() {
        when:
        Scalars.GraphQLID.getCoercing().serialize(value)
        then:
        noExceptionThrown()

        where:
        value        | _
        new Object() | _

    }

    @Unroll
    def "parseValue allows any object via String.valueOf #value"() {
        when:
        Scalars.GraphQLID.getCoercing().parseValue(value)
        then:
        noExceptionThrown()

        where:
        value        | _
        new Object() | _

    }


}
