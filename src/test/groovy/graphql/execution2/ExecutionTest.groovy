package graphql.execution2

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import spock.lang.Specification

class ExecutionTest extends Specification {


    def "test simple execution"() {
        def fooData = [id: "fooId", bar: [id: "barId", name: "someBar"]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            id: ID
            bar: Bar
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

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution();

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]


    }

    def "test execution with lists"() {
        def fooData = [[id: "fooId1", bar: [[id: "barId1", name: "someBar1"], [id: "barId2", name: "someBar2"]]],
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
            bar: [Bar]
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

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution();

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]

    }

    def "test execution with null element "() {
        def fooData = [[id: "fooId1", bar: null],
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
            bar: [Bar]
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

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]

    }

    def "test execution with null element in list"() {
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
            bar: [Bar]
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

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]

    }

    def "test execution with null element in non null list"() {
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
            bar: [Bar!]
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

        def expectedFooData = [[id: "fooId1", bar: null],
                               [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: expectedFooData]

    }

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
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: expectedFooData]

    }

    def "test execution with null element bubbling up to top "() {
        def fooData = [[id: "fooId1", bar: [[id: "barId1", name: "someBar1"], null]],
                       [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = TestUtil.schema("""
        type Query {
            foo: [Foo!]!
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


        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == null

    }

    def "test list"() {
        def fooData = [[id: "fooId1"], [id: "fooId2"], [id: "fooId3"]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = TestUtil.schema("""
        type Query {
            foo: [Foo]
        }
        type Foo {
            id: ID
        }    
        """, dataFetchers)


        def document = graphql.TestUtil.parseQuery("""
        {foo {
            id
        }}
        """)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution();

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]


    }

    def "test list in lists "() {
        def fooData = [[bar: [[id: "barId1"], [id: "barId2"]]], [bar: null], [bar: [[id: "barId3"], [id: "barId4"], [id: "barId5"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = TestUtil.schema("""
        type Query {
            foo: [Foo]
        }
        type Foo {
            bar: [Bar]
        }    
        type Bar {
            id: ID
        }
        """, dataFetchers)


        def document = graphql.TestUtil.parseQuery("""
        {foo {
            bar {
                id
            }
        }}
        """)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution();

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]


    }

    def "test simple batching with null value in list"() {
        def fooData = [[id: "fooId1"], null, [id: "fooId3"]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = TestUtil.schema("""
        type Query {
            foo: [Foo]
        }
        type Foo {
            id: ID
        }    
        """, dataFetchers)


        def document = graphql.TestUtil.parseQuery("""
        {foo {
            id
        }}
        """)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .build()


        Execution execution = new Execution();

        when:
        def monoResult = execution.execute(document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]


    }

}

