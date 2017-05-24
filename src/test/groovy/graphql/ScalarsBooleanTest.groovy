package graphql

import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsBooleanTest extends Specification {

    @Unroll
    def "Boolean parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseLiteral(literal) == result

        where:
        literal                 | result
        new BooleanValue(true)  | true
        new BooleanValue(false) | false

    }

    @Unroll
    def "Boolean returns null for invalid #literal"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseLiteral(literal) == null

        where:
        literal                                 | _
        new IntValue(12345678910 as BigInteger) | _
        new StringValue("-1")                   | _
        new FloatValue(42.3)                    | _
    }

    @Unroll
    def "Boolean serialize #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().serialize(value) == result
        Scalars.GraphQLBoolean.getCoercing().parseValue(value) == result

        where:
        value                        | result
        true                         | true
        "false"                      | false
        "true"                       | true
        "True"                       | true
        "some value not true"        | false
        ""                           | false
        0                            | false
        1                            | true
        -1                           | true
        new Long(42345784398534785l) | true
        new Double(42.3)             | true
        new Float(42.3)              | true
        Integer.MAX_VALUE + 1l       | true
        Integer.MIN_VALUE - 1l       | true
    }

    @Unroll
    def "serialize/parseValue throws exception for invalid input #value"() {
        expect:
        try {
            Scalars.GraphQLBoolean.getCoercing().serialize(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }
        try {
            Scalars.GraphQLBoolean.getCoercing().parseValue(value)
            assert false: "no exception thrown"
        } catch (GraphQLException e) {
            // expected
        }

        where:
        value        | _
        new Object() | _
    }


}
