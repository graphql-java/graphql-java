package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.incremental.IncrementalExecutionResult
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.stream.Collectors

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
    private static void assertIncrementalResults(List<Map<String, Object>> results, List<List<String>> expectedPaths) {
        assert results.size() == expectedPaths.size(), "Expected ${expectedPaths.size()} results, got ${results.size()}"

        assert results.dropRight(1).every { it.hasNext == true }, "Expected all but the last result to have hasNext=true"
        assert results.last().hasNext == false, "Expected last result to have hasNext=false"

        assert results.every { it.incremental.size() == 1 }, "Expected every result to have exactly one incremental item"

        expectedPaths.each { path ->
            assert results.any { it.incremental[0].path == path }, "Expected path $path not found in $results"
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
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 9
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
                        ["shops", 0, "departments", 0], ["shops", 0, "departments", 1],["shops", 0, "departments", 2], ["shops", 1, "departments", 0],["shops", 1, "departments", 1], ["shops", 1, "departments", 2], ["shops", 2, "departments", 0],["shops", 2, "departments", 1],["shops", 2, "departments", 2],
                        ["shops", 0, "expensiveDepartments", 0], ["shops", 0, "expensiveDepartments", 1], ["shops", 0, "expensiveDepartments", 2], ["shops", 1, "expensiveDepartments", 0], ["shops", 1, "expensiveDepartments", 1], ["shops", 1, "expensiveDepartments", 2], ["shops", 2, "expensiveDepartments", 0], ["shops", 2, "expensiveDepartments", 1],["shops", 2, "expensiveDepartments", 2],
                        ["expensiveShops", 0, "expensiveDepartments", 0], ["expensiveShops", 0, "expensiveDepartments", 1], ["expensiveShops", 0, "expensiveDepartments", 2], ["expensiveShops", 1, "expensiveDepartments", 0], ["expensiveShops", 1, "expensiveDepartments", 1], ["expensiveShops", 1, "expensiveDepartments", 2], ["expensiveShops", 2, "expensiveDepartments", 0], ["expensiveShops", 2, "expensiveDepartments", 1],["expensiveShops", 2, "expensiveDepartments", 2],
                        ["expensiveShops", 0, "departments", 0], ["expensiveShops", 0, "departments", 1], ["expensiveShops", 0, "departments", 2], ["expensiveShops", 1, "departments", 0], ["expensiveShops", 1, "departments", 1], ["expensiveShops", 1, "departments", 2], ["expensiveShops", 2, "departments", 0], ["expensiveShops", 2, "departments", 1],["expensiveShops", 2, "departments", 2]]
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

}
