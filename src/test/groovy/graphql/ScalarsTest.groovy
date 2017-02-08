package graphql

import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.StringValue
import spock.lang.Specification
import spock.lang.Unroll

class ScalarsTest extends Specification {

    @Unroll
    def "String parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLString.getCoercing().parseLiteral(literal) == result

        where:
        literal                 | result
        new StringValue("test") | "test"
        new StringValue("1")    | "1"
        new IntValue(1)         | null
    }

    @Unroll
    def "String serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLString.getCoercing().serialize(value) == result
        Scalars.GraphQLString.getCoercing().parseValue(value) == result

        where:
        value         | result
        Boolean.FALSE | "false"
        "test"        | "test"
        null          | null
    }

    @Unroll
    def "Char parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLChar.getCoercing().parseLiteral(literal) == result

        where:
        literal               | result
        new FloatValue(4)     | null
        new IntValue(4)       | null
        new StringValue("nn") | null
        new StringValue("\n") | '\n'
        new StringValue("n")  | 'n'
        new StringValue("")   | null
        null                  | null
    }

    @Unroll
    def "Char serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLChar.getCoercing().serialize(value) == result
        Scalars.GraphQLChar.getCoercing().parseValue(value) == result

        where:
        value  | result
        "11.3" | null
        "2"    | '2'
        4      | null
        'k'    | 'k'
        ""     | null
        "\n"   | '\n'
        null   | null
    }

    @Unroll
    def "ID parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLID.getCoercing().parseLiteral(literal) == result

        where:
        literal                               | result
        new StringValue("5457486ABSBHS4w646") | "5457486ABSBHS4w646"
        new IntValue(BigInteger.ONE)          | "1"
    }

    @Unroll
    def "ID serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLID.getCoercing().serialize(value) == result
        Scalars.GraphQLID.getCoercing().parseValue(value) == result

        where:
        value                | result
        "5457486ABSBHS4w646" | "5457486ABSBHS4w646"
        1                    | "1"
        null                 | null
    }

    @Unroll
    def "Boolean parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLBoolean.getCoercing().parseLiteral(literal) == result

        where:
        literal                 | result
        new BooleanValue(true)  | true
        new BooleanValue(false) | false
        new IntValue(1)         | null
    }

    @Unroll
    def "Boolean serialize/parseValue #value into #result (#result.class)"() {
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
        -1      | false
        null    | null
    }

    @Unroll
    def "#scalar.name parse error for too big/small literal"() {
        when:
        scalar.getCoercing().parseLiteral(new IntValue(intValue))

        then:
        thrown(GraphQLException)

        where:
        scalar               | intValue
        Scalars.GraphQLLong  | BigInteger.valueOf(Long.MIN_VALUE) - 1l
        Scalars.GraphQLLong  | BigInteger.valueOf(Long.MAX_VALUE) + 1l
        Scalars.GraphQLInt   | Integer.MIN_VALUE - 1l
        Scalars.GraphQLInt   | Integer.MAX_VALUE + 1l
        Scalars.GraphQLShort | Short.MIN_VALUE - 1l
        Scalars.GraphQLShort | Short.MAX_VALUE + 1l
        Scalars.GraphQLByte  | Byte.MIN_VALUE - 1l
        Scalars.GraphQLByte  | Byte.MAX_VALUE + 1l
    }

    @Unroll
    def "#scalar.name literal #literal.value number format exception"() {
        when:
        scalar.getCoercing().parseLiteral(literal)

        then:
        thrown(NumberFormatException)

        where:
        scalar                    | literal
        Scalars.GraphQLBigInteger | new StringValue("1.0")
        Scalars.GraphQLBigInteger | new StringValue("foo")
        Scalars.GraphQLBigDecimal | new StringValue("foo")
        Scalars.GraphQLLong       | new StringValue("foo")
    }

    @Unroll
    def "Long parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLLong.getCoercing().parseLiteral(literal) == result

        where:
        literal                                           | result
        new StringValue("42")                             | 42
        new FloatValue(42.3)                              | null
        new IntValue(-1)                                  | -1
        new IntValue(new BigInteger("42"))                | 42
        new IntValue(new BigInteger("42345784398534785")) | 42345784398534785l
    }


    @Unroll
    def "Long serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLLong.getCoercing().serialize(value) == result
        Scalars.GraphQLLong.getCoercing().parseValue(value) == result

        where:
        value                        | result
        "42"                         | 42
        new Long(42345784398534785l) | 42345784398534785l
        new Integer(42)              | 42
        "-1"                         | -1
        null                         | null
        //
        // lenient value support
        "42.3"                       | 42
        new Byte("42")               | 42
        new Short("42")              | 42
        new Double(42.3)             | 42
        new Float(42.3)              | 42
        new BigDecimal(42.3)         | 42
        new BigInteger(42)           | 42
    }

    @Unroll
    def "Int parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLInt.getCoercing().parseLiteral(literal) == result

        where:
        literal               | result
        new IntValue(42)      | 42
        new StringValue("-1") | null
        new FloatValue(42.3)  | null

    }

    @Unroll
    def "Int serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLInt.getCoercing().serialize(value) == result
        Scalars.GraphQLInt.getCoercing().parseValue(value) == result

        where:
        value                        | result
        "42"                         | 42
        new Integer(42)              | 42
        "-1"                         | -1
        null                         | null
        //
        // lenient value support
        "42.3"                       | 42
        new Byte("42")               | 42
        new Short("42")              | 42
        new Long(42345784398534785l) | 1020221569 // loss of precision in this case
        new Double(42.3)             | 42
        new Float(42.3)              | 42
        new BigDecimal(42.3)         | 42
        new BigInteger(42)           | 42
    }

    @Unroll
    def "Short parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLShort.getCoercing().parseLiteral(literal) == result

        where:
        literal                            | result
        new IntValue(-1)                   | -1
        new IntValue(new BigInteger("42")) | 42
    }


    @Unroll
    def "Short serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLShort.getCoercing().serialize(value) == result
        Scalars.GraphQLShort.getCoercing().parseValue(value) == result

        where:
        value                | result
        "42"                 | new Short("42")
        new Integer(42)      | 42
        "-1"                 | -1
        null                 | null
        //
        // lenient value support
        "42.3"               | 42
        new Byte("42")       | 42
        new Short("42")      | 42
        new Long(42)         | 42
        new Double(42.3)     | 42
        new Float(42.3)      | 42
        new BigDecimal(42.3) | 42
        new BigInteger(42)   | 42
        //
        // narrow casting (shorts and bytes wrap)
        Long.MAX_VALUE       | -1
        Integer.MAX_VALUE    | -1
        Short.MAX_VALUE      | Short.MAX_VALUE
        Byte.MAX_VALUE       | Byte.MAX_VALUE
    }

    @Unroll
    def "Byte parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLByte.getCoercing().parseLiteral(literal) == result

        where:
        literal                            | result
        new IntValue(-1)                   | -1
        new IntValue(new BigInteger("42")) | 42
    }


    @Unroll
    def "Byte serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLByte.getCoercing().serialize(value) == result
        Scalars.GraphQLByte.getCoercing().parseValue(value) == result

        where:
        value                | result
        "42"                 | 42
        "-1"                 | -1
        null                 | null
        //
        // lenient value support
        "42.3"               | 42
        new Byte("42")       | 42
        new Short("42")      | 42
        new Long(42)         | 42
        new Double(42.3)     | 42
        new Float(42.3)      | 42
        new BigDecimal(42.3) | 42
        new BigInteger(42)   | 42
        //
        // narrow casting (shorts and bytes wrap)
        Long.MAX_VALUE       | -1
        Integer.MAX_VALUE    | -1
        Short.MAX_VALUE      | -1
        Byte.MAX_VALUE       | Byte.MAX_VALUE
    }


    @Unroll
    def "Float parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().parseLiteral(literal) == result

        where:
        literal                | result
        new FloatValue(42.3)   | 42.3d
        new IntValue(42)       | 42.0d
        new StringValue("foo") | null
        new StringValue("-1")  | null
        new StringValue("1e2") | null
        new StringValue("1.0") | null
    }

    @Unroll
    def "Float serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLFloat.getCoercing().serialize(value) == result
        Scalars.GraphQLFloat.getCoercing().parseValue(value) == result

        where:
        value                        | result
        "11.3"                       | 11.3d
        "24.0"                       | 24.0d
        42.3f                        | 42.3f
        10                           | 10.0d
        90.000004d                   | 90.000004d
        null                         | null
        //
        // lenient value support
        "42.3"                       | 42.3
        new Byte("42")               | 42
        new Short("42")              | 42
        new Integer("42")            | 42
        new Long(42345784398534785l) | 42345784398534785l
        new BigDecimal(42.3)         | 42.3
        new BigInteger(42)           | 42
    }


    @Unroll
    def "BigInteger parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLBigInteger.getCoercing().parseLiteral(literal) == result

        where:
        literal                                                                | result
        new FloatValue(42.3)                                                   | null
        new IntValue(-1)                                                       | -1
        new IntValue(new BigInteger("42"))                                     | 42
        new IntValue(new BigInteger("42345784398534785"))                      | 42345784398534785l
        new StringValue("423457843985347854234578439853478542345784398534785") | new BigInteger("423457843985347854234578439853478542345784398534785")
    }


    @Unroll
    def "BigInteger serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLBigInteger.getCoercing().serialize(value) == result
        Scalars.GraphQLBigInteger.getCoercing().parseValue(value) == result

        where:
        value                                                 | result
        "42"                                                  | 42
        new Long(42345784398534785l)                          | 42345784398534785l
        new Integer(42)                                       | 42
        "-1"                                                  | -1
        null                                                  | null
        "423457843985347854234578439853478542345784398534785" | new BigInteger("423457843985347854234578439853478542345784398534785")
        //
        // lenient value support
        new Byte("42")                                        | 42
        new Short("42")                                       | 42
        new Integer("42")                                     | 42
        new Float("42.3")                                     | 42
        new Double("42.3")                                    | 42
        new BigDecimal(42.3)                                  | 42
        new BigInteger(42)                                    | 42
    }

    @Unroll
    def "BigDecimal parse literal #literal.value as #result"() {
        expect:
        Scalars.GraphQLBigDecimal.getCoercing().parseLiteral(literal) == result

        where:
        literal                                                                  | result
        new FloatValue(42.3)                                                     | 42.3d
        new IntValue(42)                                                         | 42.0d
        new StringValue("-1")                                                    | -1
        new StringValue("1e999")                                                 | new BigDecimal("1e999")
        new StringValue("423457843985347854234578439853478542345784398534785.0") | new BigDecimal("423457843985347854234578439853478542345784398534785")
    }

    @Unroll
    def "BigDecimal serialize/parseValue #value into #result (#result.class)"() {
        expect:
        Scalars.GraphQLBigDecimal.getCoercing().serialize(value) == result
        Scalars.GraphQLBigDecimal.getCoercing().parseValue(value) == result

        where:
        value                        | result
        "11.3"                       | 11.3d
        "24.0"                       | 24.0d
        42.3f                        | 42.3f
        10                           | 10.0d
        90.000004d                   | 90.000004d
        null                         | null
        //
        // lenient value support
        new Byte("42")               | 42
        new Short("42")              | 42
        new Integer("42")            | 42
        new Long(42345784398534785l) | 42345784398534785l
        new BigInteger(42)           | 42
        new BigDecimal(42.3)         | new BigDecimal(42.3)
    }
}
