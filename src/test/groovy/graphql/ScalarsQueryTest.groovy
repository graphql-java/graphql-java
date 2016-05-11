package graphql

import spock.lang.Specification

class ScalarsQueryTest extends Specification {

    def 'Large BigIntegers'() {
        given:
        def query = """
        query BigInteger {
          bigInteger
          i1: bigIntegerInput(input: 1234567890123456789012345678901234567890)
          i2: bigIntegerInput(input: "1234567890123456789012345678901234567890")
        }
        """
        def expected = [
                bigInteger: 9999,
                i1: 1234567890123456789012345678901234567890,
                i2: 1234567890123456789012345678901234567890
        ]

        when:
        def result = new GraphQL(ScalarsQuerySchema.scalarsQuerySchema).execute(query).data

        then:
        result == expected
    }
    
    def 'Large BigDecimals'() {
        given:
        def query = """
        query BigDecimal {
          bigDecimal
          d1: bigDecimalInput(input: "1234567890123456789012345678901234567890.0")
          d2: bigDecimalInput(input: 1234567890123456789012345678901234567890.0)
        }
        """
        def expected = [
                bigDecimal: 1234.0,
                d1: 1234567890123456789012345678901234567890.0,
                d2: 1234567890123456789012345678901234567890.0
        ]

        when:
        def result = new GraphQL(ScalarsQuerySchema.scalarsQuerySchema).execute(query).data

        then:
        result == expected
    }
}
