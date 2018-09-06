package graphql.execution

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.StarWarsSchema
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.execution.instrumentation.streaming.StreamingJsonInstrumentation
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

    def "test streaming"() {

        def jsonStream = new JacksonJsonStream(System.out)

        def instrumentation = new StreamingJsonInstrumentation({ ->
            jsonStream
        })

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(instrumentation)
                //.instrumentation(new PathOrder())
                .build()

        def badQuery = '''
        { 
            hero {
                name
                friends {
                    name
                    friends {
                        name
                    }
                }
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
        when:
        def result = graphQL.execute(badQuery)
        then:

        System.out.printf("\n\n------------------------------------------------\n")
        System.out.printf("JSON direct (scroll up for results)\n")
        System.out.printf("----------------------------------------------------\n")
        def jsonGenerator = JacksonJsonStream.mkJsonGenerator(System.out)
        jsonGenerator.writeObject(result.data)
        System.out.printf("\n\n------------------------------------------------\n")
    }

}
