package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.incremental.IncrementalExecutionResult
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

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

        assertIncrementalResults(incrementalResults, [["shops", 0], ["shops", 1], ["shops", 3]])

        when:
        def combined = combineExecutionResults(result.toSpecification(), incrementalResults)
        then:
        combined.errors == null
        combined.data == expectedData
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

        IncrementalExecutionResult result = graphQL.execute(executionInput)

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
    }

    def "query with top-level deferred field"() {
        given:
        def query = """
            query { 
                shops { 
                    expensiveDepartments {
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

        assertIncrementalResults(incrementalResults,
                [
                        []
                ]
        )

        when:
        def combined = combineExecutionResults(result.toSpecification(), incrementalResults)
        then:
        combined.errors == null
        combined.data == expectedData
    }

    def "query with multiple deferred fields"() {
        given:
        def query = getExpensiveQuery(true)

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
        // Ordering is non-deterministic, so we assert on the things we know are going to be true.

        incrementalResults.size() == 3
        // only the last payload has "hasNext=true"
        incrementalResults[0].hasNext == true
        incrementalResults[1].hasNext == true
        incrementalResults[2].hasNext == false

        // every payload has only 1 incremental item
        incrementalResults.every { it.incremental.size() == 1 }

        // path is different for every payload
        incrementalResults.any { it.incremental[0].path == ["shops", 0] }
        incrementalResults.any { it.incremental[0].path == ["shops", 1] }
        incrementalResults.any { it.incremental[0].path == ["shops", 2] }

        when:
        def combined = combineExecutionResults(result.toSpecification(), incrementalResults)
        then:
        combined.errors == null
        combined.data == expectedExpensiveData
    }

}
