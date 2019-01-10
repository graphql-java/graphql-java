package graphql.execution3

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.ExecutionId
import graphql.execution2.Execution
import graphql.schema.DataFetcher
import graphql.language.OperationDefinition
import graphql.language.OperationDefinition.Operation
import graphql.language.Field
import spock.lang.Ignore
import spock.lang.Specification

class ExecutionPlanBuilderTest extends Specification {
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

        def builder = new ExecutionPlanBuilder()
            .schema(schema)
            .document(document)
            .operation(null)
        
        when:
        def plan = builder.build()
        
        def Query = plan.getNode new OperationVertex(new OperationDefinition(null, Operation.QUERY), schema.getType("Query"))
        def Query_foo = plan.getNode new FieldVertex(new Field("foo"), schema.getType("Foo"), schema.getType("Query"))
        def Foo_id = plan.getNode new FieldVertex(new Field("id"), schema.getType("ID"), schema.getType("Foo"))
        def Foo_bar = plan.getNode new FieldVertex(new Field("bar"), schema.getType("Bar"), schema.getType("Foo"))
        def Bar_id = plan.getNode new FieldVertex(new Field("id"), schema.getType("ID"), schema.getType("Bar"))    
        def Bar_name = plan.getNode new FieldVertex(new Field("name"), schema.getType("String"), schema.getType("Bar"))    
        
        def order = plan.orderDependencies()

        then:
        plan.order() == 6
        
        order.hasNext() == true
        order.next() == [Query_foo] as Set
        order.hasNext() == true
        order.next() == [Foo_id, Foo_bar] as Set
        order.hasNext() == true
        order.next() == [Bar_id, Bar_name] as Set
        order.hasNext() == true
        order.next() == [Query] as Set
        order.hasNext() == false
    }
/*
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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
        def monoResult = execution.execute(BatchedExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]


    }
*/
}

