package graphql.execution3

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import spock.lang.Specification
import spock.lang.Ignore

class OneDAGExecutionStrategyTest extends Specification {

//    @Ignore
    def "test execution with null element bubbling up because of non null "() {
        def fooData = [[id: "fooId1", bar: [[id: "barId1", name: "someBar1"], null]],
                       [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = TestUtil.schema("""
        type Query {
            foo: [Foo]
        }
        type Foo {
            id: ID
            bar: [Bar!]!
        }    
        type Bar {
            id: ID
            name: String
        }
        """, dataFetchers)


        def document = graphql.TestUtil.parseQuery("""
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """)

        def expectedFooData = [null,
                               [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DAGExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: expectedFooData]

    }
}

