package graphql.execution3

import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import graphql.schema.GraphQLType
import graphql.language.OperationDefinition
import graphql.language.OperationDefinition.Operation
import graphql.language.Field
import graphql.language.Node
import graphql.util.DependencyGraphContext
import graphql.util.Edge
import spock.lang.Ignore
import spock.lang.Specification

class TestGraphContext implements ExecutionPlanContext {
    void prepareResolve (Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge) {
    }
    
    void whenResolved (Edge<? extends NodeVertex<? extends Node, ? extends GraphQLType>, ?> edge) {
    }
    
    boolean resolve (NodeVertex<? extends Node, ? extends GraphQLType> node) {
        if (node instanceof DocumentVertex)
            return true
            
        return false
    }
}

class ExecutionPlanBuilderTest extends Specification {
    //@Ignore
    def "test simple query"() {
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

        def builder = ExecutionPlan.newExecutionPlanBuilder()
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
        
        def order = plan.orderDependencies(new TestGraphContext())

        then:
        plan.order() == 7
        
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

    //@Ignore
    def "test simple execution with inline fragments"() {
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
            ... on Foo {
                id
                bar {
                    ... on Bar {
                        id
                        name
                    }
                    id
                    name
                }
            }
            bar {
                id
                name
            }
        }}
        """)

        def builder = ExecutionPlan.newExecutionPlanBuilder()
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
        
        def order = plan.orderDependencies(new TestGraphContext())

        then:
        plan.order() == 7
        
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

    //@Ignore
    def "test simple execution with redundant inline fragments"() {
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
            ... on Foo {
                id
                bar {
                    ... on Bar {
                        id
                        name
                    }
                    id
                    name
                }
            }
            ... on Foo {
                id
                bar {
                    ... on Bar {
                        id
                        name
                    }
                    id
                    name
                }
            }
            bar {
                ... on Bar {
                    id
                    name
                }
                id
                name
            }
        }}
        """)

        def builder = ExecutionPlan.newExecutionPlanBuilder()
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
        
        def order = plan.orderDependencies(new TestGraphContext())

        then:
        plan.order() == 7
        
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
    
    //@Ignore
    def "test simple execution with fragment spreads"() {
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
            ...F1
            bar {
                id
                name
            }
        }}
        fragment F1 on Foo {
            id
            bar {
                ...B1
                id
                name
            }
        }
        fragment B1 on Bar {
            id
            name
        }
        """)

        def builder = ExecutionPlan.newExecutionPlanBuilder()
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
        
        def order = plan.orderDependencies(new TestGraphContext())

        then:
        plan.order() == 7
        
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
    
    //@Ignore
    def "test simple execution with redundant fragment spreads"() {
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
            ...F1
            ...F1
            bar {
                id
                name
            }
        }}
        fragment F1 on Foo {
            id
            bar {
                ...B1
                ...B1
                id
                name
            }
            ...F2
        }
        fragment F2 on Foo {
            id
            bar {
                id
                name
            }
        }
        fragment B1 on Bar {
            id
            name
            ...B2
        }
        fragment B2 on Bar {
            id
            name
        }
        """)

        def builder = ExecutionPlan.newExecutionPlanBuilder()
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
        
        def order = plan.orderDependencies(new TestGraphContext())

        then:
        plan.order() == 7
        
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
    
    //@Ignore
    def "test simple query with aliases"() {
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
            bar1: bar {
                id
                name
            }
        }}
        """)

        def builder = ExecutionPlan.newExecutionPlanBuilder()
            .schema(schema)
            .document(document)
            .operation(null)
        
        when:
        def plan = builder.build()
        
        def Query = plan.getNode new OperationVertex(new OperationDefinition(null, Operation.QUERY), schema.getType("Query"))
        def Query_foo = plan.getNode new FieldVertex(new Field("foo"), schema.getType("Foo"), schema.getType("Query"))
        def Foo_id = plan.getNode new FieldVertex(new Field("id"), schema.getType("ID"), schema.getType("Foo"))
        def Foo_bar = plan.getNode new FieldVertex(new Field("bar"), schema.getType("Bar"), schema.getType("Foo"))
        def Foo_bar1 = plan.getNode new FieldVertex(Field.newField("bar").alias("bar1").build(), schema.getType("Bar"), schema.getType("Foo"))
        def Bar_id = plan.getNode new FieldVertex(new Field("id"), schema.getType("ID"), schema.getType("Bar"))    
        def Bar_name = plan.getNode new FieldVertex(new Field("name"), schema.getType("String"), schema.getType("Bar"))    
        def Bar1_id = plan.getNode new FieldVertex(new Field("id"), schema.getType("ID"), schema.getType("Bar"), Foo_bar1)    
        def Bar1_name = plan.getNode new FieldVertex(new Field("name"), schema.getType("String"), schema.getType("Bar"), Foo_bar1)    
        
        def order = plan.orderDependencies(new TestGraphContext())

        then:
        plan.order() == 10
        
        order.hasNext() == true
        order.next() == [Query_foo] as Set
        order.hasNext() == true
        order.next() == [Foo_id, Foo_bar, Foo_bar1] as Set
        order.hasNext() == true
        order.next() == [Bar_id, Bar_name, Bar1_id, Bar1_name] as Set
        order.hasNext() == true
        order.next() == [Query] as Set
        order.hasNext() == false
    }
}

