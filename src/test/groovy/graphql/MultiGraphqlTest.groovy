package graphql

import graphql.execution.instrumentation.NoOpInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionResultParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.newExecutionInput
import static graphql.GraphQL.newGraphQL
import static graphql.StarWarsSchema.starWarsSchema

class MultiGraphqlTest extends Specification {

    def "multi operations works as expected"() {
        when:
        GraphQL graphQL = newGraphQL(starWarsSchema).build()

        def query = '''
            query A {
                hero {
                    name
                }
            }    

            query B {
                human(id:"1000") {
                    name
                }
            }    
        '''
        ExecutionInput input = newExecutionInput().operationNames(["A", "B"]).query(query).build()

        def er = graphQL.execute(input)


        then:
        er.errors.size() == 0
        List<CompletableFuture<ExecutionResult>> results = er.getData()
        results.size() == 2

        def erA = results[0].join()
        def erB = results[1].join()

        erA.data == [hero: [name: "R2-D2"]]
        erB.data == [human: [name: "Luke Skywalker"]]
    }

    def "errors and extensions are handled"() {
        when:

        def bangInstrumentation = new NoOpInstrumentation() {
            @Override
            DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters) {
                return new DataFetcher<Object>() {
                    @Override
                    Object get(DataFetchingEnvironment env) {
                        if (env.fieldDefinition.name == "appearsIn") {
                            throw new RuntimeException("Bang")
                        }
                        return dataFetcher.get(env)
                    }
                }
            }

            @Override
            CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult, InstrumentationExecutionResultParameters parameters) {
                def ext = ["extensionKey": parameters.operation]
                def er = new ExecutionResultImpl(executionResult.data, executionResult.errors, ext)
                return CompletableFuture.completedFuture(er)
            }
        }

        GraphQL graphQL = newGraphQL(starWarsSchema).instrumentation(bangInstrumentation).build()

        def query = '''
            query A {
                hero {
                    name
                    appearsIn
                }
            }    

            query B {
                human(id:"1000") {
                    name
                    appearsIn
                }
            }    
        '''
        ExecutionInput input = newExecutionInput().operationNames(["A", "B"]).query(query).build()

        def er = graphQL.execute(input)


        then:
        List<CompletableFuture<ExecutionResult>> results = er.getData()
        results.size() == 2
        def erA = results[0].join()
        def erB = results[1].join()

        erA.data == [hero: [name: "R2-D2", appearsIn: null]]
        erB.data == [human: [name: "Luke Skywalker", appearsIn: null]]

        erA.errors.size() == 1
        erB.errors.size() == 1

        erA.extensions == [extensionKey: "A"]
        erB.extensions == [extensionKey: "B"]
    }
}
