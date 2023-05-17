package graphql.execution.values.legacycoercing

import graphql.GraphQLContext
import graphql.schema.GraphQLInputType
import spock.lang.Specification

import java.util.function.BiConsumer

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString

class LegacyCoercingInputInterceptorTest extends Specification {

    def "can detect legacy boolean values"() {
        when:
        def isLegacyValue = LegacyCoercingInputInterceptor.isLegacyValue(input, inputType)
        then:
        isLegacyValue == expected

        where:
        input       | inputType      | expected
        "true"      | GraphQLBoolean | true
        "false"     | GraphQLBoolean | true
        "TRUE"      | GraphQLBoolean | true
        "FALSE"     | GraphQLBoolean | true
        "junk"      | GraphQLBoolean | true
        // not acceptable to the old
        true        | GraphQLBoolean | false
        false       | GraphQLBoolean | false
        ["rubbish"] | GraphQLBoolean | false
    }

    def "can change legacy boolean values"() {
        def interceptor = LegacyCoercingInputInterceptor.migratesValues()
        when:
        def value = interceptor.intercept(input, inputType, GraphQLContext.getDefault(), Locale.getDefault())
        then:
        value == expected

        where:
        input       | inputType      | expected
        "true"      | GraphQLBoolean | true
        "false"     | GraphQLBoolean | false
        "TRUE"      | GraphQLBoolean | true
        "FALSE"     | GraphQLBoolean | false

        // left alone
        "junk"      | GraphQLBoolean | "junk"
        true        | GraphQLBoolean | true
        false       | GraphQLBoolean | false
        ["rubbish"] | GraphQLBoolean | ["rubbish"]
    }

    def "can detect legacy float values"() {
        when:
        def isLegacyValue = LegacyCoercingInputInterceptor.isLegacyValue(input, inputType)
        then:
        isLegacyValue == expected

        where:
        input       | inputType    | expected
        "1.0"       | GraphQLFloat | true
        "1"         | GraphQLFloat | true
        "junk"      | GraphQLFloat | true
        // not acceptable to the old
        666.0F      | GraphQLFloat | false
        666         | GraphQLFloat | false
        ["rubbish"] | GraphQLFloat | false
    }

    def "can change legacy float values"() {
        def interceptor = LegacyCoercingInputInterceptor.migratesValues()
        when:
        def value = interceptor.intercept(input, inputType, GraphQLContext.getDefault(), Locale.getDefault())
        then:
        value == expected

        where:
        input       | inputType    | expected
        "1.0"       | GraphQLFloat | 1.0F
        "1"         | GraphQLFloat | 1.0F

        // left alone
        "junk"      | GraphQLFloat | "junk"
        666.0F      | GraphQLFloat | 666.0F
        666         | GraphQLFloat | 666
        ["rubbish"] | GraphQLFloat | ["rubbish"]
    }

    def "can detect legacy int values"() {
        when:
        def isLegacyValue = LegacyCoercingInputInterceptor.isLegacyValue(input, inputType)
        then:
        isLegacyValue == expected

        where:
        input       | inputType  | expected
        "1.0"       | GraphQLInt | true
        "1"         | GraphQLInt | true
        "junk"      | GraphQLInt | true
        // not acceptable to the old
        666.0F      | GraphQLInt | false
        666         | GraphQLInt | false
        ["rubbish"] | GraphQLInt | false
    }

    def "can change legacy int values"() {
        def interceptor = LegacyCoercingInputInterceptor.migratesValues()
        when:
        def value = interceptor.intercept(input, inputType, GraphQLContext.getDefault(), Locale.getDefault())
        then:
        value == expected

        where:
        input       | inputType  | expected
        "1.0"       | GraphQLInt | 1
        "1"         | GraphQLInt | 1

        // left alone
        "junk"      | GraphQLInt | "junk"
        666.0F      | GraphQLInt | 666.0F
        666         | GraphQLInt | 666
        ["rubbish"] | GraphQLInt | ["rubbish"]
    }

    def "can detect legacy String values"() {
        when:
        def isLegacyValue = LegacyCoercingInputInterceptor.isLegacyValue(input, inputType)
        then:
        isLegacyValue == expected

        where:
        input       | inputType     | expected
        666.0F      | GraphQLString | true
        666         | GraphQLString | true
        ["rubbish"] | GraphQLString | true

        // strings that are strings dont need to change
        "xyz"       | GraphQLString | false
        "abc"       | GraphQLString | false
        "junk"      | GraphQLString | false

    }

    def "can change legacy String values"() {
        def interceptor = LegacyCoercingInputInterceptor.migratesValues()
        when:
        def value = interceptor.intercept(input, inputType, GraphQLContext.getDefault(), Locale.getDefault())
        then:
        value == expected
        where:
        // its just String.valueOf()
        input       | inputType     | expected
        "xyz"       | GraphQLString | "xyz"
        "abc"       | GraphQLString | "abc"
        "junk"      | GraphQLString | "junk"
        666.0F      | GraphQLString | "666.0"
        666         | GraphQLString | "666"
        ["rubbish"] | GraphQLString | "[rubbish]"
    }

    def "can observe values "() {
        def lastValue = null
        def lastType = null

        def callback = new BiConsumer<Object, GraphQLInputType>() {
            @Override
            void accept(Object o, GraphQLInputType graphQLInputType) {
                lastValue = o
                lastType = graphQLInputType
            }
        }
        def interceptor = LegacyCoercingInputInterceptor.observesValues(callback)
        when:
        lastValue = null
        lastType = null
        def value = interceptor.intercept(input, inputType, GraphQLContext.getDefault(), Locale.getDefault())

        then:
        // nothing changes - it observes only
        value == input
        lastValue == expectedLastValue
        lastType == expectedLastType

        where:
        input  | inputType      | expectedLastValue | expectedLastType
        "true" | GraphQLBoolean | "true"            | GraphQLBoolean
        "1.0"  | GraphQLFloat   | "1.0"             | GraphQLFloat
        "1"    | GraphQLInt     | "1"               | GraphQLInt
        1      | GraphQLString  | 1                 | GraphQLString

        // no observation if its not needed
        true   | GraphQLBoolean | null              | null
        1.0F   | GraphQLFloat   | null              | null
        1      | GraphQLInt     | null              | null
        "x"    | GraphQLString  | null              | null

    }
}
