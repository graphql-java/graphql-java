package graphql

import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsTest extends Specification {

    def "String parse literal"() {
        expect:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal) == result

        where:
        literal                 | result
        new StringValue("test") | "test"
    }

    def "String serialize/parseValue object"() {
        expect:
        Scalars.GraphQLString.getCoercing().serialize(value) == result
        Scalars.GraphQLString.getCoercing().parseValue(value) == result

        where:
        value         | result
        Boolean.FALSE | "false"
        "test"        | "test"
        null          | null
    }


    def "ID parse literal"() {
        expect:
        Scalars.GraphQLID.getCoercing().parseLiteral(literal) == result

        where:
        literal                               | result
        new StringValue("5457486ABSBHS4w646") | "5457486ABSBHS4w646"
    }

    def "ID serialize/parseValue object"() {
        expect:
        Scalars.GraphQLID.getCoercing().serialize(value) == result
        Scalars.GraphQLID.getCoercing().parseValue(value) == result

        where:
        value                | result
        "5457486ABSBHS4w646" | "5457486ABSBHS4w646"
        null                 | null
    }

    def "Long parse literal"() {
        expect:
        Scalars.GraphQLLong.getCoercing().parseLiteral(literal) == result

        where:
        literal               | result
        new StringValue("42") | 42
        new IntValue("42")      | 42
    }


    def "Long serialize/parseValue object"() {
        expect:
        Scalars.GraphQLLong.getCoercing().serialize(value) == result
        Scalars.GraphQLLong.getCoercing().parseValue(value) == result

        where:
        value                        | result
        "42"                         | 42
        new Long(42345784398534785l) | 42345784398534785l
        new Integer(42)              | 42
        null                         | null
    }


    def "Int parse literal"() {
        expect:
        Scalars.GraphQLInt.getCoercing().parseLiteral(literal) == result

        where:
        literal          | result
        new IntValue("42") | 42

    }

    def "Int serialize/parseValue object"() {
        expect:
        Scalars.GraphQLInt.getCoercing().serialize(value) == result
        Scalars.GraphQLInt.getCoercing().parseValue(value) == result

        where:
        value           | result
        "42"            | 42
        new Integer(42) | 42
        null            | null
    }

    def "Float parse literal"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().parseLiteral(literal) == result

        where:
        literal              | result
        new FloatValue(42.3) | 42.3d
    }

    @Unroll
    def "Float serialize/parseValue #value into #result"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().serialize(value) == result
        Scalars.GraphQLFloat.getCoercing().parseValue(value) == result

        where:
        value      | result
        "11.3"     | 11.3d
        "24.0"     | 24.0d
        42.3f      | 42.3f
        10         | 10.0d
        90.000004d | 90.000004d
        null       | null
    }

    def "Boolean parse literal"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseLiteral(literal) == result

        where:
        literal                | result
        new BooleanValue(true) | true
    }

    def "Boolean serialize/parseValue object"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().serialize(value) == result
        Scalars.GraphQLBoolean.getCoercing().parseValue(value) == result

        where:
        value   | result
        true    | true
        "false" | false
        "true"  | true
        0       | false
        1       | true
        null    | null
    }


}
