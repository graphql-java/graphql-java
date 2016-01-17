package graphql

import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import spock.lang.Specification


class ScalarsTest extends Specification {

    def "String parse literal"() {
        expect:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal) == result

        where:
        literal                 | result
        new StringValue("test") | "test"
    }

    def "String serialize object"() {
        expect:
        Scalars.GraphQLString.getCoercing().serialize(value) == result

        where:
        value         | result
        Boolean.FALSE | "false"
        "test"        | "test"
    }

    def "ID parse literal"() {
        expect:
        Scalars.GraphQLID.getCoercing().parseLiteral(literal) == result

        where:
        literal                               | result
        new StringValue("5457486ABSBHS4w646") | "5457486ABSBHS4w646"
    }

    def "ID serialize object"() {
        expect:
        Scalars.GraphQLID.getCoercing().serialize(value) == result

        where:
        value                | result
        "5457486ABSBHS4w646" | "5457486ABSBHS4w646"
    }

    def "Int parse literal"() {
        expect:
        Scalars.GraphQLInt.getCoercing().parseLiteral(literal) == result

        where:
        literal          | result
        new IntValue(42) | 42
    }

    def "Long serialize object"() {
        expect:
        Scalars.GraphQLLong.getCoercing().serialize(value) == result

        where:
        value                        | result
        "42"                         | 42
        new Long(42345784398534785l) | 42345784398534785l
        new Integer(42)              | 42
    }

    def "Long parse literal"() {
        expect:
        Scalars.GraphQLLong.getCoercing().parseLiteral(literal) == result

        where:
        literal               | result
        new StringValue("42") | 42
        new IntValue(42)      | 42
    }

    def "Int serialize object"() {
        expect:
        Scalars.GraphQLInt.getCoercing().serialize(value) == result

        where:
        value           | result
        "42"            | 42
        new Integer(42) | 42
    }

    def "Float parse literal"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().parseLiteral(literal) == result

        where:
        literal              | result
        new FloatValue(42.3) | 42.3f
    }

    def "Float serialize object"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().serialize(value) == result

        where:
        value           | result
        "42.3"          | 42.3f
        "42.0"          | 42.0f
        new Float(42.3) | 42.3f
    }

    def "Boolean parse literal"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseLiteral(literal) == result

        where:
        literal                | result
        new BooleanValue(true) | true
    }

    def "Boolean serialize object"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().serialize(value) == result

        where:
        value   | result
        true    | true
        "false" | false
        "true"  | true
        0       | false
        1       | true
    }


}
