package graphql

import graphql.language.IntValue
import graphql.language.StringValue
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsCharTest extends Specification {

    @Unroll
    def "Char parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLChar.getCoercing().parseLiteral(literal) == result

        where:
        literal              | result
        new StringValue("a") | 'a'
        new StringValue("b") | 'b'

    }

    @Unroll
    def "Short returns null for invalid #literal"() {
        expect:
        Scalars.GraphQLChar.getCoercing().parseLiteral(literal) == null

        where:
        literal                        | _
        new StringValue("aa")          | _
        new IntValue(12 as BigInteger) | _
    }

    @Unroll
    def "Short serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLChar.getCoercing().serialize(value) == result
        Scalars.GraphQLChar.getCoercing().parseValue(value) == result

        where:
        value | result
        "a"   | 'a'
        'z'   | 'z'
    }

    @Unroll
    def "serialize/parseValue throws exception for invalid input #value"() {
        expect:
        try {
            Scalars.GraphQLChar.getCoercing().serialize(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }
        try {
            Scalars.GraphQLChar.getCoercing().parseValue(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }

        where:
        value        | _
        ""           | _
        "aa"         | _
        new Object() | _

    }


}
