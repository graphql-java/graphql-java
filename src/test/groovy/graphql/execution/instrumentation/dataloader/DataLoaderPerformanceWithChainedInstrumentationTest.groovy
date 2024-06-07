package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.incremental.IncrementalExecutionResult
import org.dataloader.DataLoaderRegistry
import spock.lang.Ignore
import spock.lang.Specification

import static graphql.ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.assertIncrementalExpensiveData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.expectedExpensiveData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.expectedInitialDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.expectedListOfDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getDeferredQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveDeferredQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getIncrementalResults
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getQuery

class DataLoaderPerformanceWithChainedInstrumentationTest extends Specification {

    GraphQL graphQL
    DataLoaderRegistry dataLoaderRegistry
    BatchCompareDataFetchers batchCompareDataFetchers


    void setup() {
        batchCompareDataFetchers = new BatchCompareDataFetchers()
        DataLoaderPerformanceData dataLoaderPerformanceData = new DataLoaderPerformanceData(batchCompareDataFetchers)

        dataLoaderRegistry = dataLoaderPerformanceData.setupDataLoaderRegistry()
        graphQL = dataLoaderPerformanceData.setupGraphQL()
    }

    def "chainedInstrumentation: 760 ensure data loader is performant for lists"() {
        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                .build()

        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        incrementalSupport << [true, false]
    }

    @Ignore("This test flakes on Travis for some reason.  Clearly this indicates some sort of problem to investigate.  However it also stop releases.")
    def "chainedInstrumentation: 970 ensure data loader is performant for multiple field with lists"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(expensiveQuery)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                .build()

        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        incrementalSupport << [true, false]
    }

    def "chainedInstrumentation: ensure data loader is performant for lists using async batch loading"() {

        when:

        batchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                .build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        incrementalSupport << [true, false]
    }

    def "chainedInstrumentation: 970 ensure data loader is performant for multiple field with lists using async batch loading"() {

        when:

        batchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(expensiveQuery)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                .build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() <= 2
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() <= 2

        where:
        incrementalSupport << [true, false]
    }

    def "chainedInstrumentation: data loader will not work with deferred queries"() {
        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(deferredQuery)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        graphQL.execute(executionInput)

        then:
        def exception = thrown(UnsupportedOperationException)
        exception.message == "Data Loaders cannot be used to resolve deferred fields"
    }

    def "chainedInstrumentation: data loader will work with deferred queries"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(deferredQuery)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        IncrementalExecutionResult result = graphQL.execute(executionInput)

        then:
        result.toSpecification() == expectedInitialDeferredData

        when:
        def incrementalResults = getIncrementalResults(result)

        then:

        println(incrementalResults)
        // Ordering is non-deterministic, so we assert on the things we know are going to be true.

        incrementalResults.size() == 3
        // only the last payload has "hasNext=true"
        incrementalResults[0].hasNext == true
        incrementalResults[1].hasNext == true
        incrementalResults[2].hasNext == false

        // every payload has only 1 incremental item, and the data is the same for all of them
        incrementalResults.every { it.incremental.size() == 1 }
        incrementalResults.every { it.incremental[0].data == [wordCount: 45999] }

        // path is different for every payload
        incrementalResults.any { it.incremental[0].path == ["posts", 0] }
        incrementalResults.any { it.incremental[0].path == ["posts", 1] }
        incrementalResults.any { it.incremental[0].path == ["posts", 2] }

        //  With deferred results, we don't achieve the same efficiency.
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3
    }


    @Ignore("Resolution of deferred fields via Data loaders is not yet supported")
    def "chainedInstrumentation: data loader will work with deferred queries on multiple levels deep"() {
        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .query(expensiveDeferredQuery)
                .dataLoaderRegistry(dataLoaderRegistry)
                .build()

        IncrementalExecutionResult result = graphQL.execute(executionInput)

        then:
        result.toSpecification() == expectedInitialDeferredData

        when:
        def incrementalResults = getIncrementalResults(result)


        then:
        assertIncrementalExpensiveData(incrementalResults)

        // With deferred results, we don't achieve the same efficiency.
        // The final number of loader calls is non-deterministic, so we can't assert an exact number.
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() >= 3
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() >= 3
    }
}
