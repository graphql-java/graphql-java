package graphql.config

import graphql.ExecutionInput
import graphql.ExperimentalApi
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys
import graphql.execution.instrumentation.dataloader.DelayedDataLoaderDispatcherExecutorFactory
import graphql.execution.ResponseMapFactory
import graphql.introspection.GoodFaithIntrospection
import graphql.parser.ParserOptions
import graphql.schema.PropertyDataFetcherHelper
import spock.lang.Specification

import java.time.Duration

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

    def "can set data loader chaining config for enablement"() {
        when:
        def graphqlContextBuilder = GraphQLContext.newContext()
        GraphQL.unusualConfiguration(graphqlContextBuilder).dataloaderConfig().enableDataLoaderChaining(true)

        then:
        graphqlContextBuilder.build().get(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING) == true
        GraphQL.unusualConfiguration(graphqlContextBuilder).dataloaderConfig().isDataLoaderChainingEnabled()


        when:
        def graphqlContext = GraphQLContext.newContext().build()
        GraphQL.unusualConfiguration(graphqlContext).dataloaderConfig().enableDataLoaderChaining(true)

        then:
        graphqlContext.get(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING) == true
        GraphQL.unusualConfiguration(graphqlContext).dataloaderConfig().isDataLoaderChainingEnabled()
    }

    def "can set data loader chaining config for extra config"() {
        when:
        def graphqlContext = GraphQLContext.newContext().build()
        GraphQL.unusualConfiguration(graphqlContext).dataloaderConfig().delayedDataLoaderBatchWindowSize(Duration.ofMillis(10))

        then:
        graphqlContext.get(DataLoaderDispatchingContextKeys.DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS) == Duration.ofMillis(10).toNanos()
        GraphQL.unusualConfiguration(graphqlContext).dataloaderConfig().delayedDataLoaderBatchWindowSize() == Duration.ofMillis(10)

        when:
        DelayedDataLoaderDispatcherExecutorFactory factory = {}
        graphqlContext = GraphQLContext.newContext().build()
        GraphQL.unusualConfiguration(graphqlContext).dataloaderConfig().delayedDataLoaderExecutorFactory(factory)

        then:
        graphqlContext.get(DataLoaderDispatchingContextKeys.DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY) == factory
        GraphQL.unusualConfiguration(graphqlContext).dataloaderConfig().delayedDataLoaderExecutorFactory() == factory

        when:
        graphqlContext = GraphQLContext.newContext().build()
        // just to show we we can navigate the DSL
        GraphQL.unusualConfiguration(graphqlContext)
                .incrementalSupport()
                .enableIncrementalSupport(false)
                .enableIncrementalSupport(true)
                .then()
                .dataloaderConfig()
                .enableDataLoaderChaining(true)
                .then()
                .dataloaderConfig()
                .delayedDataLoaderBatchWindowSize(Duration.ofMillis(10))
                .delayedDataLoaderExecutorFactory(factory)

        then:
        graphqlContext.get(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING) == true
        graphqlContext.get(DataLoaderDispatchingContextKeys.DELAYED_DATA_LOADER_BATCH_WINDOW_SIZE_NANO_SECONDS) == Duration.ofMillis(10).toNanos()
        graphqlContext.get(DataLoaderDispatchingContextKeys.DELAYED_DATA_LOADER_DISPATCHING_EXECUTOR_FACTORY) == factory
    }

    def "we can access via the ExecutionInput"() {
        when:
        def eiBuilder = ExecutionInput.newExecutionInput("query q {f}")

        GraphQL.unusualConfiguration(eiBuilder)
                .dataloaderConfig()
                .enableDataLoaderChaining(true)

        def ei = eiBuilder.build()

        then:
        ei.getGraphQLContext().get(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING) == true

        when:
        ei = ExecutionInput.newExecutionInput("query q {f}").build()

        GraphQL.unusualConfiguration(ei)
                .dataloaderConfig()
                .enableDataLoaderChaining(true)

        then:
        ei.getGraphQLContext().get(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING) == true
  }

    def "can set response map factory"() {
        def rfm1 = new ResponseMapFactory() {
            @Override
            Map<String, Object> createInsertionOrdered(List<String> keys, List<Object> values) {
                return null
            }
        }

        def rfm2 = new ResponseMapFactory() {
            @Override
            Map<String, Object> createInsertionOrdered(List<String> keys, List<Object> values) {
                return null
            }
        }

        when:
        def graphqlContextBuilder = GraphQLContext.newContext()
        GraphQL.unusualConfiguration(graphqlContextBuilder).responseMapFactory().setFactory(rfm1)

        then:
        GraphQL.unusualConfiguration(graphqlContextBuilder).responseMapFactory().getOr(rfm2) == rfm1

        when:
        graphqlContextBuilder = GraphQLContext.newContext()

        then: "can default"
        GraphQL.unusualConfiguration(graphqlContextBuilder).responseMapFactory().getOr(rfm2) == rfm2

        when:
        def graphqlContext = GraphQLContext.newContext().build()
        GraphQL.unusualConfiguration(graphqlContext).responseMapFactory().setFactory(rfm1)

        then:
        GraphQL.unusualConfiguration(graphqlContext).responseMapFactory().getOr(rfm2) == rfm1

        when:
        graphqlContext = GraphQLContext.newContext().build()

        then: "can default"
        GraphQL.unusualConfiguration(graphqlContext).responseMapFactory().getOr(rfm2) == rfm2

    }
}
