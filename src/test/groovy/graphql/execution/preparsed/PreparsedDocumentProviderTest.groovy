package graphql.execution.preparsed

import graphql.ErrorType
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.LegacyTestingInstrumentation
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.language.Document
import graphql.parser.Parser
import spock.lang.Specification

import java.util.function.Function

import static graphql.ExecutionInput.newExecutionInput
import static graphql.execution.instrumentation.SimpleInstrumentationContext.noOp

class PreparsedDocumentProviderTest extends Specification {

    def 'Preparse of simple serial execution'() {
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
        given:

        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """


        def expectedPreparsed = [
                "start:execution",

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

        when:

        def instrumentation = new LegacyTestingInstrumentation()
        def instrumentationPreparsed = new LegacyTestingInstrumentation()
        def preparsedCache = new TestingPreparsedDocumentProvider()

        def strategy = new AsyncExecutionStrategy()
        def data1 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentation)
                .preparsedDocumentProvider(preparsedCache)
                .build()
                .execute(newExecutionInput().query(query).build()).data

        def data2 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentationPreparsed)
                .preparsedDocumentProvider(preparsedCache)
                .build()
                .execute(newExecutionInput().query(query).build()).data


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
                .execute(newExecutionInput().query(query).build())

        def result2 = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(preparsedCache)
                .build()
                .execute(newExecutionInput().query(query).build())

        then: "Both the first and the second result should give the same validation error"
        result1.errors.size() == 1
        result2.errors.size() == 1
        result1.errors == result2.errors

        result1.errors[0].errorType == ErrorType.ValidationError
        result1.errors[0].errorType == result2.errors[0].errorType
    }

    class InputCapturingInstrumentation extends SimplePerformantInstrumentation {
        ExecutionInput capturedInput

        @Override
        InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters, InstrumentationState state) {
            capturedInput = parameters.getExecutionInput()
            return noOp()
        }
    }

    def "swapping pre-parser will pass on swapped query"() {

        def queryA = """
              query A {
                  hero {
                      id
                  }
              }
              """
        def queryB = """
              query B {
                  hero {
                      name
                  }
              }
              """

        def documentProvider = new PreparsedDocumentProvider() {

            @Override
            PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
                if (executionInput.getQuery() == "#A") {
                    executionInput = executionInput.transform({ it.query(queryA) })
                } else {
                    executionInput = executionInput.transform({ it.query(queryB) })
                }
                return parseAndValidateFunction.apply(executionInput)
            }
        }

        def instrumentationA = new InputCapturingInstrumentation()
        def resultA = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(documentProvider)
                .instrumentation(instrumentationA)
                .build()
                .execute(newExecutionInput().query("#A").build())

        def instrumentationB = new InputCapturingInstrumentation()
        def resultB = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .preparsedDocumentProvider(documentProvider)
                .instrumentation(instrumentationB)
                .build()
                .execute(newExecutionInput().query("#B").build())

        expect:

        resultA.data == [hero: [id: "2001"]]
        instrumentationA.capturedInput.getQuery() == queryA

        resultB.data == [hero: [name: "R2-D2"]]
        instrumentationB.capturedInput.getQuery() == queryB
    }

    def "sync method and async method result is same"() {
        given:
        def provider = new TestingPreparsedDocumentProvider()
        def queryA = """
              query A {
                  hero {
                      id
                  }
              }
              """
        def engineParser = {
            ExecutionInput ei ->
                def doc = new Parser().parseDocument(ei.getQuery())
                return new PreparsedDocumentEntry(doc)
        }
        when:
        def syncMethod = provider.getDocument(newExecutionInput(queryA).build(), engineParser)
        def asyncMethod = provider.getDocumentAsync(newExecutionInput(queryA).build(), engineParser)

        then:
        asyncMethod != null
        asyncMethod.get().equals(syncMethod)
    }
}
