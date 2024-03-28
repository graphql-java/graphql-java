package graphql.execution


import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.newExecutionInput

class MaterialisedAndPromisedObjectsTest extends Specification {

    def sdl = """
        type Query {
            foo : Foo
        }
        
        type Foo {
            bar : Bar
            name : String
        }
        
        type Bar {
            foo : Foo
            name : String
        }
    """

    def "make sure it can fetch both materialised and promised values"() {

        def cfPromisesOnFieldRegex = ~"neverMatchesAlwaysMaterialised"
        Instrumentation fetchSwitcher = new SimplePerformantInstrumentation() {
            @Override
            DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
                return new DataFetcher<Object>() {
                    @Override
                    Object get(DataFetchingEnvironment env) throws Exception {
                        def fieldName = env.getField().name
                        def fetchValue = dataFetcher.get(env)
                        // if it matches the regex - we send back an async promise value
                        if (fieldName =~ cfPromisesOnFieldRegex) {
                            return CompletableFuture.supplyAsync { -> fetchValue }
                        }
                        // just the materialised value!
                        return fetchValue
                    }
                }
            }
        }

        GraphQL graphQL = TestUtil.graphQL(sdl).instrumentation(fetchSwitcher).build()


        def source = [foo: [bar: [foo: [name: "stop"]]]]
        def expectedData = [foo: [bar: [foo: [name: "stop"]]]]

        def query = """ { foo { bar { foo { name }}}} """


        when: "always materialised - no promises"

        cfPromisesOnFieldRegex = ~"neverMatchesAlwaysMaterialised"
        ExecutionResult er = graphQL.execute(newExecutionInput(query).root(source))


        then:
        er.errors.isEmpty()
        er.data == expectedData

        when: "everything is promises"

        cfPromisesOnFieldRegex = ~".*"
        er = graphQL.execute(newExecutionInput(query).root(source))

        then:
        er.errors.isEmpty()
        er.data == expectedData


        when: "only foo fields are CF promises so a mix of materialised and promised values"

        cfPromisesOnFieldRegex = ~"foo"
        er = graphQL.execute(newExecutionInput(query).root(source))

        then:
        er.errors.isEmpty()
        er.data == expectedData
    }
}
