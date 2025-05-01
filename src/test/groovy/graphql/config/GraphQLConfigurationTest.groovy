package graphql.config

import graphql.GraphQL
import graphql.GraphQLContext
import graphql.parser.ParserOptions
import spock.lang.Specification

import static graphql.parser.ParserOptions.newParserOptions

class GraphQLConfigurationTest extends Specification {

    def startingParserOptions = ParserOptions.getDefaultParserOptions()

    void cleanup() {
        ParserOptions.setDefaultParserOptions(startingParserOptions)
        GraphQL.config().propertyDataFetcher().setUseNegativeCache(true)
    }

    def "can set parser configurations"() {
        when:
        def parserOptions = newParserOptions().maxRuleDepth(99).build()
        GraphQL.config().parser().setDefaultParserOptions(parserOptions)
        def defaultParserOptions = GraphQL.config().parser().getDefaultParserOptions()

        then:
        defaultParserOptions.getMaxRuleDepth() == 99
    }

    def "can set property data fetcher config"() {
        when:
        def prevValue = GraphQL.config().propertyDataFetcher().setUseNegativeCache(false)
        then:
        prevValue

        when:
        prevValue = GraphQL.config().propertyDataFetcher().setUseNegativeCache(false)
        then:
        ! prevValue

        when:
        prevValue = GraphQL.config().propertyDataFetcher().setUseNegativeCache(true)
        then:
        ! prevValue
    }

    def "can set defer configuration"() {
        when:
        def builder = GraphQLContext.newContext()
        GraphQL.config().incrementalSupport().enableIncrementalSupport(builder, true)

        then:
        GraphQL.config().incrementalSupport().isIncrementalSupportEnabled(builder.build())

        when:
        builder = GraphQLContext.newContext()
        GraphQL.config().incrementalSupport().enableIncrementalSupport(builder, false)

        then:
        !GraphQL.config().incrementalSupport().isIncrementalSupportEnabled(builder.build())

        when:
        builder = GraphQLContext.newContext()

        then:
        !GraphQL.config().incrementalSupport().isIncrementalSupportEnabled(builder.build())
    }
}
