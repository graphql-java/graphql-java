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

    def "ID coerce literal"() {
        expect:
        Scalars.GraphQLID.getCoercing().coerceLiteral(literal) == result

        where:
        literal                 | result
        new StringValue("5457486ABSBHS4w646") | "5457486ABSBHS4w646"
    }

    def "ID coerce object"() {
        expect:
        Scalars.GraphQLID.getCoercing().coerce(value) == result

        where:
        value         | result
        "5457486ABSBHS4w646"        | "5457486ABSBHS4w646"
    }

    def "Int coerce literal"() {
        expect:
        Scalars.GraphQLInt.getCoercing().coerceLiteral(literal) == result

        where:
        literal          | result
        new IntValue(42) | 42
    }

    def "Long coerce object"() {
        expect:
        Scalars.GraphQLLong.getCoercing().coerce(value) == result

        where:
        value                        | result
        "42"                         | 42
        new Long(42345784398534785l) | 42345784398534785l
        new Integer(42)              | 42
    }

    def "Long coerce literal"() {
        expect:
        Scalars.GraphQLLong.getCoercing().coerceLiteral(literal) == result

        where:
        literal               | result
        new StringValue("42") | 42
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
