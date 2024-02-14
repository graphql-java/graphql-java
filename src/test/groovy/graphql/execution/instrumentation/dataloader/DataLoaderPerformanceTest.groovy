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
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getDeferredQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedListOfDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveDeferredQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getIncrementalResults
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getQuery

class DataLoaderPerformanceTest extends Specification {

    GraphQL graphQL
    DataLoaderRegistry dataLoaderRegistry
    BatchCompareDataFetchers batchCompareDataFetchers

    void setup() {
        batchCompareDataFetchers = new BatchCompareDataFetchers()
        DataLoaderPerformanceData dataLoaderPerformanceData = new DataLoaderPerformanceData(batchCompareDataFetchers)
        dataLoaderRegistry = dataLoaderPerformanceData.setupDataLoaderRegistry()
        graphQL = dataLoaderPerformanceData.setupGraphQL()
    }

    def "760 ensure data loader is performant for lists"() {
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

    def "970 ensure data loader is performant for multiple field with lists"() {

        when:

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

    def "ensure data loader is performant for lists using async batch loading"() {

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

    def "970 ensure data loader is performant for multiple field with lists using async batch loading"() {

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

    def "data loader will not work with deferred queries"() {
        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(deferredQuery)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
                .build()

        def result = graphQL.execute(executionInput)
        println(result);

        then:
        def exception = thrown(UnsupportedOperationException)
        exception.message == "Data Loaders cannot be used to resolve deferred fields"
    }

    @Ignore("Resolution of deferred fields via Data loaders is not yet supported")
    def "data loader will work with deferred queries"() {

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
        incrementalResults == expectedListOfDeferredData

        //  With deferred results, we don't achieve the same efficiency.
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3
    }

    @Ignore("Resolution of deferred fields via Data loaders is not yet supported")
    def "data loader will work with deferred queries on multiple levels deep"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(expensiveDeferredQuery)
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): true])
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
