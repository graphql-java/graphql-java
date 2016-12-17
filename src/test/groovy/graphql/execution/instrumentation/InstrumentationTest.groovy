package graphql.execution.instrumentation

import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.ExecutionContext
import graphql.execution.SimpleExecutionStrategy
import graphql.language.Document
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
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

                "start:field-hero",
                "start:fetch-hero",
                "end:fetch-hero",

                "start:field-id",
                "start:fetch-id",
                "end:fetch-id",
                "end:field-id",

                "end:field-hero",

                "end:execution",
        ]

        when:

        def instrumentation = new Instrumentation() {

            def executionList = []

            @Override
            InstrumentationContext beginExecution(String requestString, String operationName, Object context, Map<String, Object> arguments) {
                new Timer("execution", executionList)
            }

            @Override
            InstrumentationContext beginParse(String requestString, String operationName, Object context, Map<String, Object> arguments) {
                return new Timer("parse", executionList)
            }

            @Override
            InstrumentationContext<List<ValidationError>> beginValidation(Document document) {
                return new Timer("validation", executionList)
            }

            @Override
            InstrumentationContext beginField(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef) {
                return new Timer("field-$fieldDef.name", executionList)
            }

            @Override
            InstrumentationContext beginDataFetch(ExecutionContext executionContext, GraphQLFieldDefinition fieldDef, DataFetchingEnvironment environment) {
                return new Timer("fetch-$fieldDef.name", executionList)
            }
        }

        def strategy = new SimpleExecutionStrategy()
        def graphQL = new GraphQL(StarWarsSchema.starWarsSchema, strategy, instrumentation)

        graphQL.execute(query).data

        then:

        instrumentation.executionList == expected
    }

}
