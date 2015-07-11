package graphql

import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import spock.lang.Specification


class ScalarsTest extends Specification {

    def "String coerce literal"() {
        expect:
        Scalars.GraphQLString.getCoercing().coerceLiteral(literal) == result

        where:
        literal                 | result
        new StringValue("test") | "test"
    }

    def "String coerce object"() {
        expect:
        Scalars.GraphQLString.getCoercing().coerce(value) == result

        where:
        value         | result
        Boolean.FALSE | "false"
        "test"        | "test"
    }

    def "Int coerce literal"() {
        expect:
        Scalars.GraphQLInt.getCoercing().coerceLiteral(literal) == result

        where:
        literal          | result
        new IntValue(42) | 42
    }

    def "Int coerce object"() {
        expect:
        Scalars.GraphQLInt.getCoercing().coerce(value) == result

        where:
        value           | result
        "42"            | 42
        new Integer(42) | 42
    }

    def "Float coerce literal"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().coerceLiteral(literal) == result

        where:
        literal              | result
        new FloatValue(42.3) | 42.3f
    }

    def "Float coerce object"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().coerce(value) == result

        where:
        value           | result
        "42.3"          | 42.3f
        "42.0"          | 42.0f
        new Float(42.3) | 42.3f
    }

    def "Boolean coerce literal"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().coerceLiteral(literal) == result

        where:
        literal                | result
        new BooleanValue(true) | true
    }

    def "Boolean coerce object"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().coerce(value) == result

        where:
        value   | result
        true    | true
        "false" | false
        "true"  | true
        0       | false
        1       | true
    }



}
