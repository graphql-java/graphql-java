package graphql

import graphql.language.BooleanValue
import graphql.language.IntValue
import graphql.language.StringValue
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
        expect:
        Scalars.GraphQLID.getCoercing().parseLiteral(literal) == null

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
        value   | result
        "123ab" | "123ab"
        123     | "123"
    }

    @Unroll
    def "serialize/parseValue throws exception for invalid input #value"() {
        expect:
        try {
            Scalars.GraphQLID.getCoercing().serialize(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }
        try {
            Scalars.GraphQLID.getCoercing().parseValue(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }

        where:
        value        | _
        new Object() | _

    }


}
