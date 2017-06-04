package graphql.execution.instrumentation

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.SimpleExecutionStrategy
import graphql.execution.instrumentation.parameters.*
import graphql.language.Document
import graphql.validation.ValidationError
import spock.lang.Specification

class InstrumentationTest extends Specification {

    class Timer<T> implements InstrumentationContext<T> {
        def op
        def start = System.currentTimeMillis()
        def executionList = []

        Timer(op, executionList) {
            this.op = op
            this.executionList = executionList
            executionList << "start:$op"
            println("Started $op...")
        }

        def end() {
            this.executionList << "end:$op"
            def ms = System.currentTimeMillis() - start
            println("\tEnded $op in ${ms}ms")
        }

        @Override
        void onEnd(T result) {
            end()
        }

        @Override
        void onEnd(Exception e) {
            end()
        }
    }


    def 'Instrumentation of simple serial execution'() {
        given:

        def query = """
        query HeroNameAndFriendsQuery {
            hero {
                id
            }
        }
        """

        //
        // for testing purposes we must use SimpleExecutionStrategy under the covers to get such
        // serial behaviour.  The Instrumentation of a parallel strategy would be much different
        // and certainly harder to test
        def expected = [
                "start:execution",

                "start:parse",
                "end:parse",

                "start:validation",
                "end:validation",

                "start:data-fetch",

                "start:field-hero",
                "start:fetch-hero",
                "end:fetch-hero",

                "start:field-id",
                "start:fetch-id",
                "end:fetch-id",
                "end:field-id",

                "end:field-hero",

                "end:data-fetch",

                "end:execution",
        ]

        when:

        def instrumentation = new Instrumentation() {

            def executionList = []

            @Override
            InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
                new Timer("execution", executionList)
            }

            @Override
            InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
                return new Timer("parse", executionList)
            }

            @Override
            InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
                return new Timer("validation", executionList)
            }

            @Override
            InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
                return new Timer("data-fetch", executionList)
            }

            @Override
            InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
                return new Timer("field-$parameters.field.name", executionList)
            }

            @Override
            InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
                return new Timer("fetch-$parameters.field.name", executionList)
            }
        }

        def strategy = new SimpleExecutionStrategy()
        def graphQL = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .instrumentation(instrumentation)
                .build()

        graphQL.execute(query).data

        then:

        instrumentation.executionList == expected
    }

}
