package graphql.execution.nextgen


import graphql.nextgen.GraphQL
import graphql.schema.DataFetcher
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput
import static graphql.TestUtil.schema

class BatchedExecutionStrategyTest extends Specification {

    def "test simple execution"() {
        def fooData = [id: "fooId", bar: [id: "barId", name: "someBar"]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
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


        def query = """
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """


        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))
        then:
        result.getData() == [foo: fooData]
    }

    def "test execution with lists"() {
        def fooData = [[id: "fooId1", bar: [[id: "barId1", name: "someBar1"], [id: "barId2", name: "someBar2"]]],
                       [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
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


        def query = """
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """

        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))
        then:
        result.getData() == [foo: fooData]
    }

    def "test execution with null element "() {
        def fooData = [[id: "fooId1", bar: null],
                       [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
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


        def query = """
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """
        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))

        then:
        result.getData() == [foo: fooData]

    }

    def "test execution with null element in list"() {
        def fooData = [[id: "fooId1", bar: [[id: "barId1", name: "someBar1"], null]],
                       [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
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


        def query = """
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """
        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))
        then:
        result.getData() == [foo: fooData]
    }

    def "test execution with null element in non null list"() {
        def fooData = [[id: "fooId1", bar: [[id: "barId1", name: "someBar1"], null]],
                       [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
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


        def query = """
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """
        def expectedFooData = [[id: "fooId1", bar: null],
                               [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]

        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))
        then:
        result.getData() == [foo: expectedFooData]
    }

    def "test execution with null element bubbling up because of non null "() {
        def fooData = [[id: "fooId1", bar: [[id: "barId1", name: "someBar1"], null]],
                       [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
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


        def query = """
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """

        def expectedFooData = [null,
                               [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]

        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))
        then:
        result.getData() == [foo: expectedFooData]
    }

    def "test execution with null element bubbling up to top "() {
        def fooData = [[id: "fooId1", bar: [[id: "barId1", name: "someBar1"], null]],
                       [id: "fooId2", bar: [[id: "barId3", name: "someBar3"], [id: "barId4", name: "someBar4"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
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


        def query = """
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """
        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))
        then:
        result.getData() == null
    }

    def "test list"() {
        def fooData = [[id: "fooId1"], [id: "fooId2"], [id: "fooId3"]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
        type Query {
            foo: [Foo]
        }
        type Foo {
            id: ID
        }    
        """, dataFetchers)


        def query = """
        {foo {
            id
        }}
        """

        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))
        then:
        result.getData() == [foo: fooData]
    }

    def "test list in lists "() {
        def catsBatchSize = 0
        def catsCallCount = 0
        def idsBatchSize = 0
        def idsCallCount = 0

        def catsDataFetcher = { env ->
            catsCallCount++
            catsBatchSize = env.getSource().size()
            return [["cat1", "cat2"], null, ["cat3", "cat4", "cat5"]]
        } as BatchedDataFetcher

        def idDataFetcher = { env ->
            idsCallCount++
            idsBatchSize = env.getSource().size()
            return ["catId1", "catId2", "catId3", "catId4", "catId5"]
        } as BatchedDataFetcher

        def friendsData = ["friend1", "friend2", "friend3"]
        def dataFetchers = [
                Query : [friends: { env -> friendsData } as DataFetcher],
                Person: [cats: catsDataFetcher],
                Cat   : [id: idDataFetcher]
        ]
        def schema = schema("""
        type Query {
            friends: [Person]
        }
        type Person {
            cats: [Cat]
        }    
        type Cat {
            id: ID
        }
        """, dataFetchers)


        def query = """
        {friends { 
            cats { 
                id
            }
        }}
        """

        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))
        then:
        result.getData() == [friends: [[cats: [[id: "catId1"], [id: "catId2"]]], [cats: null], [cats: [[id: "catId3"], [id: "catId4"], [id: "catId5"]]]]]
        catsCallCount == 1
        idsCallCount == 1
        catsBatchSize == 3
        idsBatchSize == 5
    }

    def "test simple batching with null value in list"() {
        def fooData = [[id: "fooId1"], null, [id: "fooId3"]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
        type Query {
            foo: [Foo]
        }
        type Foo {
            id: ID
        }    
        """, dataFetchers)


        def query = """
        {foo {
            id
        }}
        """

        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new BatchedExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput(query))

        then:
        result.getData() == [foo: fooData]
    }
}

