package graphql.config

import graphql.GraphQL
import graphql.GraphQLContext
import spock.lang.Specification

import static graphql.parser.ParserOptions.newParserOptions

class GraphQLConfigurationTest extends Specification {

    def "can set parser configurations"() {
        when:
        def parserOptions = newParserOptions().maxRuleDepth(99).build()
        GraphQL.configuration().parser().setDefaultParserOptions(parserOptions)
        def defaultParserOptions = GraphQL.configuration().parser().getDefaultParserOptions()

        then:
        defaultParserOptions.getMaxRuleDepth() == 99
    }

    def "can set defer configuration"() {
        when:
        def builder = GraphQLContext.newContext()
        GraphQL.configuration().incrementalSupport().enableIncrementalSupport(builder, true)

        then:
        GraphQL.configuration().incrementalSupport().isIncrementalSupportEnabled(builder.build())

        when:
        builder = GraphQLContext.newContext()
        GraphQL.configuration().incrementalSupport().enableIncrementalSupport(builder, false)

        then:
        !GraphQL.configuration().incrementalSupport().isIncrementalSupportEnabled(builder.build())

        when:
        builder = GraphQLContext.newContext()

        then:
        !GraphQL.configuration().incrementalSupport().isIncrementalSupportEnabled(builder.build())
    }
}
