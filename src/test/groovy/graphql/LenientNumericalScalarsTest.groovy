package graphql

import spock.lang.Specification
import spock.lang.Unroll

class LenientNumericalScalarsTest extends Specification {

    // we don't test parseLiteral since its covered by the parent class tests

    @Unroll
    def "Long serialize/parseValue #value into #result"() {
        expect:
        LenientNumericalScalars.GraphQLLong.getCoercing().serialize(value) == result
        LenientNumericalScalars.GraphQLLong.getCoercing().parseValue(value) == result

        where:
        value                        | result
        "42"                         | 42
        new Long(42345784398534785l) | 42345784398534785l
        new Integer(42)              | 42
        "-1"                         | -1
        new Double(42.5)             | 42 // lenient scalar correctness
        null                         | null
    }

    @Unroll
    def "Int serialize/parseValue #value into #result"() {
        expect:
        LenientNumericalScalars.GraphQLInt.getCoercing().serialize(value) == result
        LenientNumericalScalars.GraphQLInt.getCoercing().parseValue(value) == result

        where:
        value            | result
        "42"             | 42
        new Integer(42)  | 42
        "-1"             | -1
        new Double(42.5) | 42 // lenient scalar correctness
        new Long(42)     | 42 // lenient scalar correctness
        null             | null
    }

    @Unroll
    def "Short serialize/parseValue #value into #result"() {
        expect:
        LenientNumericalScalars.GraphQLShort.getCoercing().serialize(value) == result
        LenientNumericalScalars.GraphQLShort.getCoercing().parseValue(value) == result

        where:
        value               | result
        "42"                | 42
        Short.valueOf("42") | 42
        "-1"                | -1
        new Integer(42)     | 42 // lenient scalar correctness
        new Double(42.5)    | 42 // lenient scalar correctness
        new Long(42)        | 42 // lenient scalar correctness
        null                | null
    }

    @Unroll
    def "Byte serialize/parseValue #value into #result"() {
        expect:
        LenientNumericalScalars.GraphQLByte.getCoercing().serialize(value) == result
        LenientNumericalScalars.GraphQLByte.getCoercing().parseValue(value) == result

        where:
        value               | result
        "42"                | 42
        Byte.valueOf("42")  | 42
        "-1"                | -1
        Short.valueOf("42") | 42 // lenient scalar correctness
        new Integer(42)     | 42 // lenient scalar correctness
        new Double(42.5)    | 42 // lenient scalar correctness
        new Float(42.5)     | 42 // lenient scalar correctness
        new Long(42)        | 42 // lenient scalar correctness
        null                | null
    }

    @Unroll
    def "Float serialize/parseValue #value into #result"() {
        expect:
        LenientNumericalScalars.GraphQLFloat.getCoercing().serialize(value) == result
        LenientNumericalScalars.GraphQLFloat.getCoercing().parseValue(value) == result

        where:
        value      | result
        "11.3"     | 11.3d
        "24.0"     | 24.0d
        42.3f      | 42.3f
        10         | 10.0d
        90.000004d | 90.000004d
        null       | null
    }

    @Unroll
    def "BigInteger serialize/parseValue #value into #result"() {
        expect:
        LenientNumericalScalars.GraphQLBigInteger.getCoercing().serialize(value) == result
        LenientNumericalScalars.GraphQLBigInteger.getCoercing().parseValue(value) == result

        where:
        value                                                 | result
        "42"                                                  | 42
        new Long(42345784398534785l)                          | 42345784398534785l
        new Integer(42)                                       | 42
        "-1"                                                  | -1
        new Double(42.5)                                      | 42 // lenient scalar correctness
        new Float(42)                                         | 42 // lenient scalar correctness
        null                                                  | null
        "423457843985347854234578439853478542345784398534785" | new BigInteger("423457843985347854234578439853478542345784398534785")
    }

    @Unroll
    def "BigDecimal serialize/parseValue #value into #result"() {
        expect:
        LenientNumericalScalars.GraphQLBigDecimal.getCoercing().serialize(value) == result
        LenientNumericalScalars.GraphQLBigDecimal.getCoercing().parseValue(value) == result

        where:
        value      | result
        "11.3"     | 11.3d
        "24.0"     | 24.0d
        42.3f      | 42.3f
        10         | 10.0d
        90.000004d | 90.000004d
        null       | null
    }

}
