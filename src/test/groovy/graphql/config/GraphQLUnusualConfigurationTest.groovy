package graphql.config

import graphql.ExperimentalApi
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.introspection.GoodFaithIntrospection
import graphql.parser.ParserOptions
import graphql.schema.PropertyDataFetcherHelper
import spock.lang.Specification

import static graphql.parser.ParserOptions.newParserOptions

class GraphQLUnusualConfigurationTest extends Specification {

    def startingParserOptions = ParserOptions.getDefaultParserOptions()
    def startingState = GoodFaithIntrospection.isEnabledJvmWide()

    void cleanup() {
        // JVM wide so other tests can be affected
        ParserOptions.setDefaultParserOptions(startingParserOptions)
        PropertyDataFetcherHelper.setUseNegativeCache(true)
        GoodFaithIntrospection.enabledJvmWide(startingState)
    }

    def "can set parser configurations"() {
        when:
        def parserOptions = newParserOptions().maxRuleDepth(99).build()
        GraphQL.unusualConfiguration().parsing().setDefaultParserOptions(parserOptions)
        def defaultParserOptions = GraphQL.unusualConfiguration().parsing().getDefaultParserOptions()

        then:
        defaultParserOptions.getMaxRuleDepth() == 99
    }

    def "can set property data fetcher config"() {
        when:
        def prevValue = GraphQL.unusualConfiguration().propertyDataFetching().setUseNegativeCache(false)
        then:
        prevValue

        when:
        prevValue = GraphQL.unusualConfiguration().propertyDataFetching().setUseNegativeCache(false)
        then:
        !prevValue

        when:
        prevValue = GraphQL.unusualConfiguration().propertyDataFetching().setUseNegativeCache(true)
        then:
        !prevValue
    }

    def "can set good faith settings"() {
        when:
        GraphQL.unusualConfiguration().goodFaithIntrospection().enabledJvmWide(false)

        then:
        !GraphQL.unusualConfiguration().goodFaithIntrospection().isEnabledJvmWide()

        when:
        GraphQL.unusualConfiguration().goodFaithIntrospection().enabledJvmWide(true)

        then:
        GraphQL.unusualConfiguration().goodFaithIntrospection().isEnabledJvmWide()

        // showing chaining
        when:
        GraphQL.unusualConfiguration().goodFaithIntrospection()
                .enabledJvmWide(true)
                .then().goodFaithIntrospection()
                .enabledJvmWide(false)

        then:
        !GraphQL.unusualConfiguration().goodFaithIntrospection().isEnabledJvmWide()
    }

    def "can set defer configuration on graphql context objects"() {
        when:
        def graphqlContextBuilder = GraphQLContext.newContext()
        GraphQL.unusualConfiguration(graphqlContextBuilder).incrementalSupport().enableIncrementalSupport(true)

        then:
        graphqlContextBuilder.build().get(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT) == true
        GraphQL.unusualConfiguration(graphqlContextBuilder).incrementalSupport().isIncrementalSupportEnabled()

        when:
        graphqlContextBuilder = GraphQLContext.newContext()
        GraphQL.unusualConfiguration(graphqlContextBuilder).incrementalSupport().enableIncrementalSupport(false)

        then:
        graphqlContextBuilder.build().get(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT) == false
        !GraphQL.unusualConfiguration(graphqlContextBuilder).incrementalSupport().isIncrementalSupportEnabled()

        when:
        def graphqlContext = GraphQLContext.newContext().build()
        GraphQL.unusualConfiguration(graphqlContext).incrementalSupport().enableIncrementalSupport(true)

        then:
        graphqlContext.get(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT) == true
        GraphQL.unusualConfiguration(graphqlContext).incrementalSupport().isIncrementalSupportEnabled()

        when:
        graphqlContext = GraphQLContext.newContext().build()
        GraphQL.unusualConfiguration(graphqlContext).incrementalSupport().enableIncrementalSupport(false)

        then:
        graphqlContext.get(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT) == false
        !GraphQL.unusualConfiguration(graphqlContext).incrementalSupport().isIncrementalSupportEnabled()

        when:
        graphqlContext = GraphQLContext.newContext().build()
        // just to show we we can navigate the DSL
        GraphQL.unusualConfiguration(graphqlContext)
                .incrementalSupport()
                .enableIncrementalSupport(false)
                .enableIncrementalSupport(true)
                .then().incrementalSupport()
                .enableIncrementalSupport(false)

        then:
        graphqlContext.get(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT) == false
            !GraphQL.unusualConfiguration(graphqlContext).incrementalSupport().isIncrementalSupportEnabled()
    }
}
