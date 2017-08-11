package graphql.execution.preparsed

import graphql.ErrorType
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.instrumentation.TestingInstrumentation
import spock.lang.Specification

class PreparsedDocumentProviderTest extends Specification {

    def 'Preparse of simple serial execution'() {
        given:

        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """

        def expected = [
                "start:execution",

                "start:parse",
                "end:parse",

                "start:validation",
                "end:validation",

                "start:data-fetch",

                "start:execution-strategy",

                "start:field-hero",
                "start:fetch-hero",
                "end:fetch-hero",

                "start:execution-strategy",

                "start:field-id",
                "start:fetch-id",
                "end:fetch-id",
                "end:field-id",

                "end:execution-strategy",

                "end:field-hero",

                "end:execution-strategy",

                "end:data-fetch",

                "end:execution",
        ]

        def expectedPreparsed = [
                "start:execution",

                "start:data-fetch",
                "start:execution-strategy",

                "start:field-hero",
                "start:fetch-hero",
                "end:fetch-hero",

                "start:execution-strategy",
                "start:field-id",
                "start:fetch-id",
                "end:fetch-id",
                "end:field-id",
                "end:execution-strategy",

                "end:field-hero",
                "end:execution-strategy",

                "end:data-fetch",

                "end:execution",
        ]

        when:

        def instrumentation = new TestingInstrumentation()
        def instrumentationPreparsed = new TestingInstrumentation()
        def preparsedCache = new TestingPreparsedDocumentProvider()

        def strategy = new AsyncExecutionStrategy()
        def data1 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentation)
                .preparsedDocumentProvider(preparsedCache)
                .build()
                .execute(ExecutionInput.newExecutionInput().query(query).build()).data

        def data2 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentationPreparsed)
                .preparsedDocumentProvider(preparsedCache)
                .build()
                .execute(ExecutionInput.newExecutionInput().query(query).build()).data


        then:

        instrumentation.executionList == expected
        instrumentationPreparsed.executionList == expectedPreparsed
        data1 == data2
    }


    def "Preparsed query with validation failure"() {
        given: "A query on non existing field"

        def query = """
              query HeroNameAndFriendsQuery {
                  heroXXXX {
                      id
                  }
              }
              """

        when: "Executed the query twice"
        def preparsedCache = new TestingPreparsedDocumentProvider()

        def result1 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(preparsedCache)
                .build()
                .execute(ExecutionInput.newExecutionInput().query(query).build())

        def result2 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(preparsedCache)
                .build()
                .execute(ExecutionInput.newExecutionInput().query(query).build())

        then: "Both the first and the second result should give the same validation error"
        result1.errors.size() == 1
        result2.errors.size() == 1
        result1.errors == result2.errors

        result1.errors[0].errorType == ErrorType.ValidationError
        result1.errors[0].errorType == result2.errors[0].errorType
    }
}
