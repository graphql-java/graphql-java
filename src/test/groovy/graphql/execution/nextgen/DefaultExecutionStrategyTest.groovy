package graphql.execution.nextgen

import graphql.ContextPassingDataFetcher
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.TestUtil
import graphql.execution.ExecutionId
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static graphql.ExecutionInput.newExecutionInput
import static graphql.execution.DataFetcherResult.newResult

class DefaultExecutionStrategyTest extends Specification {


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


        def document = TestUtil.parseQuery("""
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """)

        ExecutionInput executionInput = newExecutionInput()
                .build()

        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


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
        def schema = TestUtil.schema("""
        type Query {
            foo: Foo
        }
        type Foo {
            id1: ID
            id2: ID
            id3: ID
        }    
        """, dataFetchers)


        def document = TestUtil.parseQuery("""
        {
            f1: foo  { id1 }
            f2: foo { id2 }
            f3: foo  { id3 }
        }
        """)
        ExecutionInput executionInput = newExecutionInput()
                .build()

        Execution execution = new Execution()

        def cfId1 = new CompletableFuture()
        def cfId2 = new CompletableFuture()
        def cfId3 = new CompletableFuture()

        when:
        execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)

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


        def document = TestUtil.parseQuery("""
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """)

        ExecutionInput executionInput = newExecutionInput()
                .build()

        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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


        def document = TestUtil.parseQuery("""
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """)

        ExecutionInput executionInput = newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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


        def document = TestUtil.parseQuery("""
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """)

        ExecutionInput executionInput = newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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


        def document = TestUtil.parseQuery("""
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

        ExecutionInput executionInput = newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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


        def document = TestUtil.parseQuery("""
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

        ExecutionInput executionInput = newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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


        def document = TestUtil.parseQuery("""
        {foo {
            id
            bar {
                id
                name
            }
        }}
        """)


        ExecutionInput executionInput = newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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


        def document = TestUtil.parseQuery("""
        {foo {
            id
        }}
        """)

        ExecutionInput executionInput = newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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


        def document = TestUtil.parseQuery("""
        {foo {
            bar {
                id
            }
        }}
        """)

        ExecutionInput executionInput = newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
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


        def document = TestUtil.parseQuery("""
        {foo {
            id
        }}
        """)

        ExecutionInput executionInput = newExecutionInput()
                .build()


        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.getData() == [foo: fooData]
    }

    def "data fetcher can return context down each level"() {
        given:

        def spec = '''
            type Query {
                first : Level1
            }
            
            type Level1 {
                second : Level2 
            }
            
            type Level2 {
                third : Level3
            }
            
            type Level3 {
                skip : Level4
            }    

            type Level4 {
                fourth : String
            }    
        '''


        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query",
                { type ->
                    type.dataFetcher("first", new ContextPassingDataFetcher())
                })
                .type("Level1",
                { type ->
                    type.dataFetcher("second", new ContextPassingDataFetcher())
                })
                .type("Level2",
                { type ->
                    type.dataFetcher("third", new ContextPassingDataFetcher())
                })
                .type("Level3",
                { type ->
                    type.dataFetcher("skip", new ContextPassingDataFetcher(true))
                })
                .type("Level4",
                { type ->
                    type.dataFetcher("fourth", new ContextPassingDataFetcher())
                })
                .build()

        def query = '''
            {
                first {
                    second {
                        third {
                            skip {
                                fourth
                            }
                        }
                    }
                }
            }
        '''

        def schema = TestUtil.schema(spec, runtimeWiring)
        def executionInput = newExecutionInput().query(query).root("").context(1).build()
        def document = TestUtil.parseQuery(query)

        Execution execution = new Execution()

        when:

        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:

        result.errors.isEmpty()
        result.data == [first: [second: [third: [skip: [fourth: "1,2,3,4,4,"]]]]]
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
        def schema = TestUtil.schema('''
        type Query {
            foo: [Foo]
        }
        type Foo {
            id: ID
        }    
        ''', dataFetchers)


        def document = TestUtil.parseQuery('''
        {
            foo {
                id
            }
        }
        ''')

        ExecutionInput executionInput = newExecutionInput().build()
        Execution execution = new Execution()

        when:
        def monoResult = execution.execute(DefaultExecutionStrategy, document, schema, ExecutionId.generate(), executionInput)
        def result = monoResult.get()


        then:
        result.errors.size() == 3
        result.data == [foo: fooData]
    }

    private static ExceptionWhileDataFetching mkError(DataFetchingEnvironment env) {
        def rte = new RuntimeException("Bang on " + env.getField().getName())
        new ExceptionWhileDataFetching(env.executionStepInfo.getPath(), rte, env.getField().sourceLocation)
    }
}
