package graphql

import graphql.schema.DataFetcher
import org.awaitility.Awaitility
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class MutationTest extends Specification {


    def "evaluates mutations"() {
        given:
        def query = """
            mutation M {
              first: changeTheNumber(newNumber: 1) {
                theNumber
              },
              second: changeTheNumber(newNumber: 2) {
                theNumber
              },
              third: changeTheNumber(newNumber: 3) {
                theNumber
              }
              fourth: changeTheNumber(newNumber: 4) {
                theNumber
              },
              fifth: changeTheNumber(newNumber: 5) {
                theNumber
              }
            }
            """

        def expectedResult = [
                first : [
                        theNumber: 1
                ],
                second: [
                        theNumber: 2
                ],
                third : [
                        theNumber: 3
                ],
                fourth: [
                        theNumber: 4
                ],
                fifth : [
                        theNumber: 5
                ]
        ]

        when:
        def ei = ExecutionInput.newExecutionInput(query).root(new MutationSchema.SubscriptionRoot(6)).build()
        def executionResult = GraphQL.newGraphQL(MutationSchema.schema).build().execute(ei)

        then:
        executionResult.data == expectedResult

    }


    def "evaluates mutations with errors"() {
        given:
        def query = """
            mutation M {
              first: changeTheNumber(newNumber: 1) {
                theNumber
              },
              second: changeTheNumber(newNumber: 2) {
                theNumber
              },
              third: failToChangeTheNumber(newNumber: 3) {
                theNumber
              }
              fourth: changeTheNumber(newNumber: 4) {
                theNumber
              },
              fifth: failToChangeTheNumber(newNumber: 5) {
                theNumber
              }
            }
            """

        def expectedResult = [
                first : [
                        theNumber: 1
                ],
                second: [
                        theNumber: 2
                ],
                third : null,
                fourth: [
                        theNumber: 4
                ],
                fifth : null
        ]

        when:
        def ei = ExecutionInput.newExecutionInput(query).root(new MutationSchema.SubscriptionRoot(6)).build()
        def executionResult = GraphQL.newGraphQL(MutationSchema.schema).build().execute(ei)

        then:
        executionResult.data == expectedResult
        executionResult.errors.size() == 2
        executionResult.errors.every({ it instanceof ExceptionWhileDataFetching })

    }

    def "simple async mutation"() {
        def sdl = """
            type Query {
                q : String
            }
            
            type Mutation {
                plus1(arg: Int) : Int
                plus2(arg: Int) : Int
                plus3(arg: Int) : Int
            }
        """

        def mutationDF = { env ->
            CompletableFuture.supplyAsync {

                def fieldName = env.getField().name
                def factor = Integer.parseInt(fieldName.substring(fieldName.length() - 1))
                def value = env.getArgument("arg")

                return value + factor
            }
        } as DataFetcher

        def schema = TestUtil.schema(sdl, [Mutation: [
                plus1: mutationDF,
                plus2: mutationDF,
                plus3: mutationDF,
        ]])

        def graphQL = GraphQL.newGraphQL(schema).build()

        when:
        def er = graphQL.execute("""
            mutation m {
                plus1(arg:10)
                plus2(arg:10)
                plus3(arg:10)
             }
        """)

        then:
        er.errors.isEmpty()
        er.data == [
                plus1: 11,
                plus2: 12,
                plus3: 13,
        ]
    }

    def "simple async mutation with DataLoader"() {
        def sdl = """
            type Query {
                q : String
            }
            
            type Mutation {
                plus1(arg: Int) : Int
                plus2(arg: Int) : Int
                plus3(arg: Int) : Int
            }
           
        """

        BatchLoader<Integer, Integer> batchLoader = { keys ->
            CompletableFuture.supplyAsync {
                return keys
            }

        } as BatchLoader


        DataLoaderRegistry dlReg = DataLoaderRegistry.newRegistry()
                .register("dl", DataLoaderFactory.newDataLoader(batchLoader))
                .build()

        def mutationDF = { env ->
            def fieldName = env.getField().name
            def factor = Integer.parseInt(fieldName.substring(fieldName.length() - 1))
            def value = env.getArgument("arg")

            def key = value + factor
            return env.getDataLoader("dl").load(key)
        } as DataFetcher

        def schema = TestUtil.schema(sdl, [Mutation: [
                plus1: mutationDF,
                plus2: mutationDF,
                plus3: mutationDF,
        ]])


        def graphQL = GraphQL.newGraphQL(schema)
                .build()


        def ei = ExecutionInput.newExecutionInput("""
            mutation m {
                plus1(arg:10)
                plus2(arg:10)
                plus3(arg:10)
             }
        """).dataLoaderRegistry(dlReg).build()
        when:
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [
                plus1: 11,
                plus2: 12,
                plus3: 13,
        ]
    }

    /*
     This test shows a dataloader being called at the mutation field level, in serial via AsyncSerialExecutionStrategy, and then
     again at the sub field level, in parallel, via AsyncExecutionStrategy.
     */
    def "more complex async mutation with DataLoader"() {
        def sdl = """
            type Query {
                q : String
            }
            
            type Mutation {
                topLevelF1(arg: Int) : ComplexType
                topLevelF2(arg: Int) : ComplexType
                topLevelF3(arg: Int) : ComplexType
                topLevelF4(arg: Int) : ComplexType
            }
            
            type ComplexType {
                f1 : ComplexType
                f2 : ComplexType
                f3 : ComplexType
                f4 : ComplexType
                end : String
            }
        """

        def emptyComplexMap = [
                f1: null,
                f2: null,
                f3: null,
                f4: null,
        ]

        BatchLoaderWithContext<Integer, Map> fieldBatchLoader = { keys, context ->
            assert keys.size() == 2, "since only f1 and f2 are DL based, we will only get 2 key values"

            def batchValue = [
                    emptyComplexMap,
                    emptyComplexMap,
            ]
            CompletableFuture.supplyAsync {
                return batchValue
            }

        } as BatchLoaderWithContext

        BatchLoader<Integer, Integer> mutationBatchLoader = { keys ->
            CompletableFuture.supplyAsync {
                return keys
            }

        } as BatchLoader


        DataLoaderRegistry dlReg = DataLoaderRegistry.newRegistry()
                .register("topLevelDL", DataLoaderFactory.newDataLoader(mutationBatchLoader))
                .register("fieldDL", DataLoaderFactory.newDataLoader(fieldBatchLoader))
                .build()

        def mutationDF = { env ->
            def fieldName = env.getField().name
            def factor = Integer.parseInt(fieldName.substring(fieldName.length() - 1))
            def value = env.getArgument("arg")

            def key = value + factor
            return env.getDataLoader("topLevelDL").load(key)
        } as DataFetcher

        def fieldDataLoaderDF = { env ->
            def fieldName = env.getField().name
            def level = env.getExecutionStepInfo().getPath().getLevel()
            return env.getDataLoader("fieldDL").load(fieldName, level)
        } as DataFetcher

        def fieldDataLoaderNonDF = { env ->
            return emptyComplexMap
        } as DataFetcher

        def schema = TestUtil.schema(sdl,
                [Mutation   : [
                        topLevelF1: mutationDF,
                        topLevelF2: mutationDF,
                        topLevelF3: mutationDF,
                        topLevelF4: mutationDF,
                ],
                 // only f1 and f3 are using data loaders - f2 and f4 are plain old property based
                 // so some fields with batch loader and some without
                 ComplexType: [
                         f1: fieldDataLoaderDF,
                         f2: fieldDataLoaderNonDF,
                         f3: fieldDataLoaderDF,
                         f4: fieldDataLoaderNonDF,
                 ]
                ])


        def graphQL = GraphQL.newGraphQL(schema)
                .build()


        def ei = ExecutionInput.newExecutionInput("""
            mutation m {
                topLevelF1(arg:10) {
                    f1 {
                        f1 { end } 
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f2 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f3 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f4 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                }
                    
                topLevelF2(arg:10) {
                    f1 {
                        f1 { end } 
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f2 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f3 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f4 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                }
                
                topLevelF3(arg:10) {
                    f1 {
                        f1 { end } 
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f2 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f3 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f4 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                }

                topLevelF4(arg:10) {
                    f1 {
                        f1 { end } 
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f2 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f3 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                    f4 {
                        f1 { end }
                        f2 { end }
                        f3 { end }
                        f4 { end }
                    }
                }
             }
        """).dataLoaderRegistry(dlReg).build()
        when:
        def cf = graphQL.executeAsync(ei)

        Awaitility.await().until { cf.isDone() }
        def er = cf.join()

        then:

        er.errors.isEmpty()

        def expectedMap = [
                f1: [f1: [end: null], f2: [end: null], f3: [end: null], f4: [end: null]],
                f2: [f1: [end: null], f2: [end: null], f3: [end: null], f4: [end: null]],
                f3: [f1: [end: null], f2: [end: null], f3: [end: null], f4: [end: null]],
                f4: [f1: [end: null], f2: [end: null], f3: [end: null], f4: [end: null]],
        ]

        er.data == [
                topLevelF1: expectedMap,
                topLevelF2: expectedMap,
                topLevelF3: expectedMap,
                topLevelF4: expectedMap,
        ]
    }
}
