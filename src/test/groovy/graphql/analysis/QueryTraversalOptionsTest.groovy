package graphql.analysis

import spock.lang.Specification

class QueryTraversalOptionsTest extends Specification {

    def "defaulting works as expected"() {
        when:
        def options = QueryTraversalOptions.defaultOptions()

        then:
        options.isCoerceFieldArguments()

        when:
        options = QueryTraversalOptions.defaultOptions().coerceFieldArguments(false)

        then:
        !options.isCoerceFieldArguments()
    }
}
