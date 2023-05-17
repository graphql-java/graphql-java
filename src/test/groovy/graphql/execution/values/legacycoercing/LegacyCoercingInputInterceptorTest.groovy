package graphql.execution.values.legacycoercing

import graphql.Scalars
import spock.lang.Specification

class LegacyCoercingInputInterceptorTest extends Specification {

    def "can detect boolean legacy values"() {
        when:
        def isLegacyValue = LegacyCoercingInputInterceptor.isLegacyValue(input, inputType)
        then:
        isLegacyValue == expected

        where:
        input       | inputType              | expected
        "true"      | Scalars.GraphQLBoolean | true
        "false"     | Scalars.GraphQLBoolean | true
        "TRUE"      | Scalars.GraphQLBoolean | true
        "FALSE"     | Scalars.GraphQLBoolean | true
        "junk"      | Scalars.GraphQLBoolean | true
        // not acceptable to the old
        true        | Scalars.GraphQLBoolean | false
        false       | Scalars.GraphQLBoolean | false
        ["rubbish"] | Scalars.GraphQLBoolean | false
    }
}
