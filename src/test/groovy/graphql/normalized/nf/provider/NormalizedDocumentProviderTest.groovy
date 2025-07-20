package graphql.normalized.nf.provider

import graphql.ExperimentalApi
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.instrumentation.LegacyTestingInstrumentation
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput

class NormalizedDocumentProviderTest extends Specification {
    def expected = [
            "start:execution",

            "start:parse",
            "end:parse",

            "start:validation",
            "end:validation",
            "start:execute-operation",

            "start:execution-strategy",

            "start:field-hero",
            "start:fetch-hero",
            "end:fetch-hero",
            "start:complete-hero",

            "start:execute-object",

            "start:field-id",
            "start:fetch-id",
            "end:fetch-id",
            "start:complete-id",
            "end:complete-id",
            "end:field-id",

            "end:execute-object",

            "end:complete-hero",
            "end:field-hero",

            "end:execution-strategy",

            "end:execute-operation",
            "end:execution",
    ]

    def expectedNormalizedCached = [
            "start:execution",

            "start:parse",
            "end:parse",

            "start:validation",
            "end:validation",
            "start:execute-operation",

            "start:execution-strategy",

            "start:field-hero",
            "start:fetch-hero",
            "end:fetch-hero",
            "start:complete-hero",

            "start:execute-object",

            "start:field-id",
            "start:fetch-id",
            "end:fetch-id",
            "start:complete-id",
            "end:complete-id",
            "end:field-id",

            "end:execute-object",

            "end:complete-hero",
            "end:field-hero",

            "end:execution-strategy",

            "end:execute-operation",
            "end:execution",
    ]

    def 'Normalized document caching of simple serial execution'() {
        given:
        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """


        when:

        def instrumentation = new LegacyTestingInstrumentation()
        def instrumentationPreparsed = new LegacyTestingInstrumentation()
        def normalizedCache = new TestingNormalizedDocumentProvider()
        def context = Map.<Object, Object>of(
                ExperimentalApi.ENABLE_NORMALIZED_DOCUMENT_SUPPORT, true)
        def executionInput = newExecutionInput().query(query).graphQLContext(context).build()

        def strategy = new AsyncExecutionStrategy()
        def data1 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentation)
                .normalizedDocumentProvider(normalizedCache)
                .build()
                .execute(executionInput).data

        def data2 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentationPreparsed)
                .normalizedDocumentProvider(normalizedCache)
                .build()
                .execute(executionInput).data


        then:

        instrumentation.executionList == expected
        instrumentationPreparsed.executionList == expectedNormalizedCached
        data1 == data2
        normalizedCache.cache.containsKey(query)
    }

    def 'Normalized document caching of simple anonymous serial execution'() {
        given:
        def query = """
        query {
            hero {
                id
            }
        }
        """


        when:

        def instrumentation = new LegacyTestingInstrumentation()
        def instrumentationPreparsed = new LegacyTestingInstrumentation()
        def normalizedCache = new TestingNormalizedDocumentProvider()
        def context = Map.<Object, Object>of(
                ExperimentalApi.ENABLE_NORMALIZED_DOCUMENT_SUPPORT, true)
        def executionInput = newExecutionInput().query(query).graphQLContext(context).build()

        def strategy = new AsyncExecutionStrategy()
        def data1 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentation)
                .normalizedDocumentProvider(normalizedCache)
                .build()
                .execute(executionInput).data

        def data2 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentationPreparsed)
                .normalizedDocumentProvider(normalizedCache)
                .build()
                .execute(executionInput).data


        then:

        instrumentation.executionList == expected
        instrumentationPreparsed.executionList == expectedNormalizedCached
        data1 == data2
        normalizedCache.cache.containsKey(query)
    }
}
