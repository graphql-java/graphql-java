package graphql

import graphql.schema.DataFetcher
import org.dataloader.BatchLoader
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

}
