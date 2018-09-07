package graphql.execution

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.execution.instrumentation.streaming.StreamingJsonInstrumentation
import graphql.execution.streaming.AsyncStreamingExecutionStrategy
import spock.lang.Specification

class TestStreamingSupport extends Specification {

    class PathOrder extends SimpleInstrumentation {
        @Override
        InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
            println "start: " + parameters.typeInfo.path + " | " + parameters.typeInfo.toAst()
            return new SimpleInstrumentationContext<ExecutionResult>() {
                @Override
                void onCompleted(ExecutionResult result, Throwable t) {
                    println "\tcomplete: " + parameters.typeInfo.path + " | " + parameters.typeInfo.toAst()
                }
            }
        }
    }

    def dumpResults(ExecutionResult executionResult) {
        System.out.printf("\n\n------------------------------------------------\n")
        System.out.printf("JSON direct (scroll up for results)\n")
        System.out.printf("----------------------------------------------------\n")
        def jsonGenerator = JacksonJsonStream.mkJsonGenerator(System.out)
        jsonGenerator.writeObject(executionResult.data)
        System.out.printf("\n\n------------------------------------------------\n")
    }

    def longerQuery = '''
        { 
            hero {
                id
                name
                friends {
                    id
                    name
                    friends {
                        id
                        name
                        appearsIn
                    }
                    appearsIn
                }
                appearsIn
            }
        }
        '''

    def goodQuery = '''
        { 
            hero {
                name
                friends {
                    name
                }
            }
        }
        '''

    def "test streaming"() {

        def jsonStream = new JacksonJsonStream(System.out)

        def instrumentation = new StreamingJsonInstrumentation({ ->
            jsonStream
        })

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(instrumentation)
        //.instrumentation(new PathOrder())
                .build()

        when:
        def result = graphQL.execute(longerQuery)
        then:

        dumpResults(result)
    }

    def "stream via execution strategy"() {
        def strategy = new AsyncStreamingExecutionStrategy({ -> new JacksonJsonStream(System.out) })


        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(strategy)
                .build()

        when:
        def result = graphQL.execute(longerQuery)
        then:

        dumpResults(result)
    }
}
