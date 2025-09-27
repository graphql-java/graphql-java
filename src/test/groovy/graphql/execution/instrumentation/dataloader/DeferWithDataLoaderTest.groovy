package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.ExperimentalApi
import graphql.GraphQL
import graphql.TestUtil
import graphql.incremental.IncrementalExecutionResult
import graphql.schema.DataFetcher
import org.awaitility.Awaitility
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import spock.lang.RepeatUntilFailure
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture

import static graphql.ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.combineExecutionResults
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.expectedData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.expectedExpensiveData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getIncrementalResults
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getQuery

class DeferWithDataLoaderTest extends Specification {

    GraphQL graphQL
    DataLoaderRegistry dataLoaderRegistry
    BatchCompareDataFetchers batchCompareDataFetchers


    void setup() {
        batchCompareDataFetchers = new BatchCompareDataFetchers()
        DataLoaderPerformanceData dataLoaderPerformanceData = new DataLoaderPerformanceData(batchCompareDataFetchers)

        dataLoaderRegistry = dataLoaderPerformanceData.setupDataLoaderRegistry()
        graphQL = dataLoaderPerformanceData.setupGraphQL()
    }

    /**
     * @param results a list of the incremental results from the execution
     * @param expectedPaths a list of the expected paths in the incremental results. The order of the elements in the list is not important.
     */
    private static void assertIncrementalResults(List<Map<String, Object>> results, List<List<String>> expectedPaths, List<Map> expectedData = null) {
        assert results.size() == expectedPaths.size(), "Expected ${expectedPaths.size()} results, got ${results.size()}"

        assert results.dropRight(1).every { it.hasNext == true }, "Expected all but the last result to have hasNext=true"
        assert results.last().hasNext == false, "Expected last result to have hasNext=false"

        assert results.every { it.incremental.size() == 1 }, "Expected every result to have exactly one incremental item"

        expectedPaths.eachWithIndex { path, index ->
            def result = results.find { it.incremental[0].path == path }
            assert result != null, "Expected path $path not found in $results"
            if (expectedData != null) {
                assert result.incremental[0].data == expectedData[index], "Expected data $expectedData[index] for path $path, got ${result.incremental[0].data}"
            }
        }
    }

    def "query with single deferred field"() {
        given:
        def query = getQuery(true, false)

        def expectedInitialData = [
                data   : [
                        shops: [
                                [id: "shop-1", name: "Shop 1"],
                                [id: "shop-2", name: "Shop 2"],
                                [id: "shop-3", name: "Shop 3"],
                        ]
                ],
                hasNext: true
        ]

        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        IncrementalExecutionResult result = graphQL.execute(executionInput)

        then:
        result.toSpecification() == expectedInitialData

        when:
        def incrementalResults = getIncrementalResults(result)

        then:

        assertIncrementalResults(incrementalResults, [["shops", 0], ["shops", 1], ["shops", 2]])

        when:
        def combined = combineExecutionResults(result.toSpecification(), incrementalResults)
        then:
        combined.errors == null
        combined.data == expectedData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3
    }

    def "multiple fields on same defer block"() {
        given:
        def query = """
            query {
                shops {
                    id
                    ... @defer {
                        name
                        departments {
                            name
                        }
                    }
                }
            }

 """

        def expectedInitialData = [
                data   : [
                        shops: [
                                [id: "shop-1"],
                                [id: "shop-2"],
                                [id: "shop-3"],
                        ]
                ],
                hasNext: true
        ]

        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        IncrementalExecutionResult result = graphQL.execute(executionInput)

        then:
        result.toSpecification() == expectedInitialData

        when:
        def incrementalResults = getIncrementalResults(result)

        then:

        assertIncrementalResults(incrementalResults, [["shops", 0], ["shops", 1], ["shops", 2]])

        when:
        def combined = combineExecutionResults(result.toSpecification(), incrementalResults)
        then:
        combined.errors == null
        combined.data == [
                shops: [
                        [id         : "shop-1", name: "Shop 1",
                         departments: [[name: "Department 1"],
                                       [name: "Department 2"],
                                       [name: "Department 3"]
                         ]],
                        [id         : "shop-2", name: "Shop 2",
                         departments: [[name: "Department 4"],
                                       [name: "Department 5"],
                                       [name: "Department 6"]
                         ]],
                        [id         : "shop-3", name: "Shop 3",
                         departments: [[name: "Department 7"],
                                       [name: "Department 8"],
                                       [name: "Department 9"]]
                        ]]
        ]
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 0
    }

    def "query with nested deferred fields"() {
        given:
        def query = getQuery(true, true)

        def expectedInitialData = [
                data   : [
                        shops: [
                                [id: "shop-1", name: "Shop 1"],
                                [id: "shop-2", name: "Shop 2"],
                                [id: "shop-3", name: "Shop 3"],
                        ]
                ],
                hasNext: true
        ]

        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        ExecutionResult result = graphQL.execute(executionInput)

        then:
        result.toSpecification() == expectedInitialData

        when:
        def incrementalResults = getIncrementalResults(result)

        then:

        assertIncrementalResults(incrementalResults,
                [
                        ["shops", 0], ["shops", 1], ["shops", 2],
                        ["shops", 0, "departments", 0], ["shops", 1, "departments", 0], ["shops", 2, "departments", 0],
                        ["shops", 0, "departments", 1], ["shops", 1, "departments", 1], ["shops", 2, "departments", 1],
                        ["shops", 0, "departments", 2], ["shops", 1, "departments", 2], ["shops", 2, "departments", 2],
                ]
        )

        when:
        def combined = combineExecutionResults(result.toSpecification(), incrementalResults)
        then:
        combined.errors == null
        combined.data == expectedData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 9
    }

    def "query with top-level deferred field"() {
        given:
        def query = """
            query { 
                shops { 
                    departments {
                        name
                    }
                } 
                ... @defer {
                    expensiveShops {
                        name
                    }
                }
            }
"""

        def expectedInitialData = [
                data   : [
                        shops: [[departments: [[name: "Department 1"], [name: "Department 2"], [name: "Department 3"]]],
                                [departments: [[name: "Department 4"], [name: "Department 5"], [name: "Department 6"]]],
                                [departments: [[name: "Department 7"], [name: "Department 8"], [name: "Department 9"]]],
                        ]
                ],
                hasNext: true
        ]

        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        IncrementalExecutionResult result = graphQL.execute(executionInput)

        then:
        result.toSpecification() == expectedInitialData

        when:
        def incrementalResults = getIncrementalResults(result)

        then:

        assertIncrementalResults(incrementalResults,
                [
                        []
                ]
        )

        when:
        def combined = combineExecutionResults(result.toSpecification(), incrementalResults)
        then:
        combined.errors == null
        combined.data == [shops         : [[departments: [[name: "Department 1"], [name: "Department 2"], [name: "Department 3"]]],
                                           [departments: [[name: "Department 4"], [name: "Department 5"], [name: "Department 6"]]],
                                           [departments: [[name: "Department 7"], [name: "Department 8"], [name: "Department 9"]]]],
                          expensiveShops: [[name: "ExShop 1"], [name: "ExShop 2"], [name: "ExShop 3"]]]

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 0
    }

    def "query with multiple deferred fields"() {
        given:
        def query = getExpensiveQuery(true)

        def expectedInitialData =
                [data   : [shops         : [[name       : "Shop 1",
                                             departments: [[name: "Department 1", products: [[name: "Product 1"]]], [name: "Department 2", products: [[name: "Product 2"]]], [name: "Department 3", products: [[name: "Product 3"]]]]],
                                            [name       : "Shop 2",
                                             departments: [[name: "Department 4", products: [[name: "Product 4"]]], [name: "Department 5", products: [[name: "Product 5"]]], [name: "Department 6", products: [[name: "Product 6"]]]]],
                                            [name       : "Shop 3",
                                             departments: [[name: "Department 7", products: [[name: "Product 7"]]], [name: "Department 8", products: [[name: "Product 8"]]], [name: "Department 9", products: [[name: "Product 9"]]]]]],
                           expensiveShops: [[name       : "ExShop 1",
                                             departments: [[name: "Department 1", products: [[name: "Product 1"]]], [name: "Department 2", products: [[name: "Product 2"]]], [name: "Department 3", products: [[name: "Product 3"]]]]],
                                            [name       : "ExShop 2",
                                             departments: [[name: "Department 4", products: [[name: "Product 4"]]], [name: "Department 5", products: [[name: "Product 5"]]], [name: "Department 6", products: [[name: "Product 6"]]]]],
                                            [name       : "ExShop 3",
                                             departments: [[name: "Department 7", products: [[name: "Product 7"]]], [name: "Department 8", products: [[name: "Product 8"]]], [name: "Department 9", products: [[name: "Product 9"]]]]]]],
                 hasNext: true]

        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        IncrementalExecutionResult result = graphQL.execute(executionInput)

        then:
        result.toSpecification() == expectedInitialData

        when:
        def incrementalResults = getIncrementalResults(result)

        then:

        assertIncrementalResults(incrementalResults,
                [
                        ["expensiveShops", 0], ["expensiveShops", 1], ["expensiveShops", 2],
                        ["shops", 0], ["shops", 1], ["shops", 2],
                        ["shops", 0, "departments", 0], ["shops", 0, "departments", 1], ["shops", 0, "departments", 2], ["shops", 1, "departments", 0], ["shops", 1, "departments", 1], ["shops", 1, "departments", 2], ["shops", 2, "departments", 0], ["shops", 2, "departments", 1], ["shops", 2, "departments", 2],
                        ["shops", 0, "expensiveDepartments", 0], ["shops", 0, "expensiveDepartments", 1], ["shops", 0, "expensiveDepartments", 2], ["shops", 1, "expensiveDepartments", 0], ["shops", 1, "expensiveDepartments", 1], ["shops", 1, "expensiveDepartments", 2], ["shops", 2, "expensiveDepartments", 0], ["shops", 2, "expensiveDepartments", 1], ["shops", 2, "expensiveDepartments", 2],
                        ["expensiveShops", 0, "expensiveDepartments", 0], ["expensiveShops", 0, "expensiveDepartments", 1], ["expensiveShops", 0, "expensiveDepartments", 2], ["expensiveShops", 1, "expensiveDepartments", 0], ["expensiveShops", 1, "expensiveDepartments", 1], ["expensiveShops", 1, "expensiveDepartments", 2], ["expensiveShops", 2, "expensiveDepartments", 0], ["expensiveShops", 2, "expensiveDepartments", 1], ["expensiveShops", 2, "expensiveDepartments", 2],
                        ["expensiveShops", 0, "departments", 0], ["expensiveShops", 0, "departments", 1], ["expensiveShops", 0, "departments", 2], ["expensiveShops", 1, "departments", 0], ["expensiveShops", 1, "departments", 1], ["expensiveShops", 1, "departments", 2], ["expensiveShops", 2, "departments", 0], ["expensiveShops", 2, "departments", 1], ["expensiveShops", 2, "departments", 2]]
        )

        when:
        def combined = combineExecutionResults(result.toSpecification(), incrementalResults)
        then:
        combined.errors == null
        combined.data == expectedExpensiveData

        // TODO: Why the load counters are only 1?
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1
    }

    @Unroll
    @RepeatUntilFailure(maxAttempts = 20, ignoreRest = false)
    def "dataloader in initial result and chained dataloader inside nested defer block"() {
        given:
        def sdl = '''
            type Query {
                pets: [Pet]
            }
            
            type Pet {
                name: String
                owner: Owner
            }
            type Owner {
                name: String
                address: String
            }
            
        '''

        def query = '''
            query {
                pets {
                    name
                    ... @defer {
                        owner {
                            name
                            ... @defer {
                                address
                            }
                        }
                    }
                }
            }
        '''

        BatchLoader petNameBatchLoader = { List<String> keys ->
            println "petNameBatchLoader called with $keys"
            assert keys.size() == 3
            return CompletableFuture.completedFuture(["Pet 1", "Pet 2", "Pet 3"])
        }
        BatchLoader addressBatchLoader = { List<String> keys ->
            println "addressBatchLoader called with $keys"
            return CompletableFuture.completedFuture(keys.collect { it ->
                if (it == "owner-1") {
                    return "Address 1"
                } else if (it == "owner-2") {
                    return "Address 2"
                } else if (it == "owner-3") {
                    return "Address 3"
                }
            })
        }

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        def petNameDL = DataLoaderFactory.newDataLoader("petName", petNameBatchLoader)
        def addressDL = DataLoaderFactory.newDataLoader("address", addressBatchLoader)
        dataLoaderRegistry.register("petName", petNameDL)
        dataLoaderRegistry.register("address", addressDL)

        DataFetcher petsDF = { env ->
            return [
                    [id: "pet-1"],
                    [id: "pet-2"],
                    [id: "pet-3"]
            ]
        }
        DataFetcher petNameDF = { env ->
            env.getDataLoader("petName").load(env.getSource().id)
        }

        DataFetcher petOwnerDF = { env ->
            String id = env.getSource().id
            if (id == "pet-1") {
                return [id: "owner-1", name: "Owner 1"]
            } else if (id == "pet-2") {
                return [id: "owner-2", name: "Owner 2"]
            } else if (id == "pet-3") {
                return [id: "owner-3", name: "Owner 3"]
            }
        }
        DataFetcher ownerAddressDF = { env ->
            return CompletableFuture.supplyAsync {
                Thread.sleep(500)
                return "foo"
            }.thenCompose {
                return env.getDataLoader("address").load(env.getSource().id)
            }
                    .thenCompose {
                        return env.getDataLoader("address").load(env.getSource().id)
                    }
        }

        def schema = TestUtil.schema(sdl, [Query: [pets: petsDF],
                                           Pet  : [name: petNameDF, owner: petOwnerDF],
                                           Owner: [address: ownerAddressDF]])
        def graphQL = GraphQL.newGraphQL(schema).build()
        def ei = ExecutionInput.newExecutionInput(query).dataLoaderRegistry(dataLoaderRegistry).build()
        ei.getGraphQLContext().put(ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT, true)
        dataLoaderChainingOrExhaustedDispatching ? ei.getGraphQLContext().put(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING, true) : ei.getGraphQLContext().put(DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING, true)

        when:
        CompletableFuture<IncrementalExecutionResult> erCF = graphQL.executeAsync(ei)
        Awaitility.await().until { erCF.isDone() }
        def er = erCF.get()

        then:
        er.toSpecification() == [data   : [pets: [[name: "Pet 1"], [name: "Pet 2"], [name: "Pet 3"]]],
                                 hasNext: true]

        when:
        def incrementalResults = getIncrementalResults(er)
        println "incrementalResults: $incrementalResults"

        then:
        assertIncrementalResults(incrementalResults,
                [
                        ["pets", 0], ["pets", 1], ["pets", 2],
                        ["pets", 0, "owner"], ["pets", 1, "owner"], ["pets", 2, "owner"],
                ],
                [
                        [owner: [name: "Owner 1"]],
                        [owner: [name: "Owner 2"]],
                        [owner: [name: "Owner 3"]],
                        [address: "Address 1"],
                        [address: "Address 2"],
                        [address: "Address 3"]
                ]
        )

        where:
        dataLoaderChainingOrExhaustedDispatching << [true, false]

    }

}
