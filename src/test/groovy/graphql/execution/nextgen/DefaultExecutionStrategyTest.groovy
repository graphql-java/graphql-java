package graphql.execution.nextgen

import graphql.ExceptionWhileDataFetching
import graphql.nextgen.GraphQL
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.newExecutionInput
import static graphql.TestUtil.schema
import static graphql.execution.DataFetcherResult.newResult

class DefaultExecutionStrategyTest extends Specification {


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
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new DefaultExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput().query(query))

        then:
        result.getData() == [foo: fooData]

    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "fields are resolved in depth in parallel"() {

        List<CompletableFuture> cfs = []

        def fooResolver = { env ->
            println env.getField()
            def result = new CompletableFuture()
            cfs << result
            result
        } as DataFetcher
        def idResolver1 = Mock(DataFetcher)
        def idResolver2 = Mock(DataFetcher)
        def idResolver3 = Mock(DataFetcher)
        def dataFetchers = [
                Query: [foo: fooResolver],
                Foo  : [id1: idResolver1, id2: idResolver2, id3: idResolver3]
        ]
        def schema = schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            id1: ID
            id2: ID
            id3: ID
        }    
        """, dataFetchers)


        def query = """
        {
            f1: foo  { id1 }
            f2: foo { id2 }
            f3: foo  { id3 }
        }
        """

        def cfId1 = new CompletableFuture()
        def cfId2 = new CompletableFuture()
        def cfId3 = new CompletableFuture()

        when:
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new DefaultExecutionStrategy()).build()
        //
        // I think this is a dangerous test - It dispatches the query but never joins on the result
        // so its expecting the DFs to be called by never resolved properly.  ??
        graphQL.executeAsync(newExecutionInput().query(query))

        then:
        cfs.size() == 3
        0 * idResolver1.get(_)
        0 * idResolver1.get(_)
        0 * idResolver1.get(_)

        when:
        cfs[1].complete(new Object())
        then:
        0 * idResolver1.get(_)
        1 * idResolver2.get(_) >> cfId2
        0 * idResolver3.get(_)

        when:
        cfs[2].complete(new Object())
        then:
        0 * idResolver1.get(_)
        0 * idResolver2.get(_)
        1 * idResolver3.get(_) >> cfId3


        when:
        cfs[0].complete(new Object())
        then:
        1 * idResolver1.get(_) >> cfId1
        0 * idResolver2.get(_)
        0 * idResolver3.get(_)

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
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new DefaultExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput().query(query))

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
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new DefaultExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput().query(query))

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
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new DefaultExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput().query(query))

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
        def graphQL = GraphQL.newGraphQL(schema).executionStrategy(new DefaultExecutionStrategy()).build()
        def result = graphQL.execute(newExecutionInput().query(query))

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
        def graphQL = GraphQL.newGraphQL(schema).build()
        def result = graphQL.execute(newExecutionInput().query(query))

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
        def graphQL = GraphQL.newGraphQL(schema).build()
        def result = graphQL.execute(newExecutionInput().query(query))

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
        def graphQL = GraphQL.newGraphQL(schema).build()
        def result = graphQL.execute(newExecutionInput().query(query))

        then:
        result.getData() == [foo: fooData]
    }

    def "test list in lists "() {
        def fooData = [[bar: [[id: "barId1"], [id: "barId2"]]], [bar: null], [bar: [[id: "barId3"], [id: "barId4"], [id: "barId5"]]]]
        def dataFetchers = [
                Query: [foo: { env -> fooData } as DataFetcher]
        ]
        def schema = schema("""
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


        def query = """
        {foo {
            bar {
                id
            }
        }}
        """
        when:
        def graphQL = GraphQL.newGraphQL(schema).build()
        def result = graphQL.execute(newExecutionInput().query(query))

        then:
        result.getData() == [foo: fooData]
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
        def graphQL = GraphQL.newGraphQL(schema).build()
        def result = graphQL.execute(newExecutionInput().query(query))

        then:
        result.getData() == [foo: fooData]
    }


    def "DataFetcherResult is respected with errors"() {

        def fooData = [[id: "fooId1"], null, [id: "fooId3"]]
        def dataFetchers = [
                Query: [
                        foo: { env ->
                            newResult().data(fooData)
                                    .error(mkError(env))
                                    .build()
                        } as DataFetcher],
                Foo  : [
                        id: { env ->
                            def id = env.source[env.getField().getName()]
                            newResult().data(id)
                                    .error(mkError(env))
                                    .build()
                        } as DataFetcher
                ]
        ]
        def schema = schema('''
        type Query {
            foo: [Foo]
        }
        type Foo {
            id: ID
        }    
        ''', dataFetchers)


        def query = '''
        {
            foo {
                id
            }
        }
        '''

        when:
        def graphQL = GraphQL.newGraphQL(schema).build()
        def result = graphQL.execute(newExecutionInput().query(query))

        then:
        result.errors.size() == 3
        result.data == [foo: fooData]
    }

    private static ExceptionWhileDataFetching mkError(DataFetchingEnvironment env) {
        def rte = new RuntimeException("Bang on " + env.getField().getName())
        new ExceptionWhileDataFetching(env.executionStepInfo.getPath(), rte, env.getField().sourceLocation)
    }
}
