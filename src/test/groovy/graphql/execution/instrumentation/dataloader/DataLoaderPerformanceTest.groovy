package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.GraphQL
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.ExperimentalApi.ENABLE_INCREMENTAL_SUPPORT
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_CHAINING
import static graphql.execution.instrumentation.dataloader.DataLoaderDispatchingContextKeys.ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.expectedExpensiveData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveQuery
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

    @Unroll
    def "760 ensure data loader is performant for lists"() {
        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(getQuery())
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                .build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        incrementalSupport | contextKey
        false              | ENABLE_DATA_LOADER_CHAINING
        true               | ENABLE_DATA_LOADER_CHAINING
        false              | ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
        true               | ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
        false              | null
        true               | null
    }

    @Unroll
    def "970 ensure data loader is performant for multiple field with lists"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(getExpensiveQuery(false))
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                .build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() <= 2
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() <= 2

        where:
        incrementalSupport | contextKey
        false              | ENABLE_DATA_LOADER_CHAINING
        true               | ENABLE_DATA_LOADER_CHAINING
        false              | ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
        true               | ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
        false              | null
        true               | null
    }

    @Unroll
    def "ensure data loader is performant for lists using async batch loading"() {

        when:

        batchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(getQuery())
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                .build()

        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        incrementalSupport | contextKey
        false              | ENABLE_DATA_LOADER_CHAINING
        true               | ENABLE_DATA_LOADER_CHAINING
        false              | ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
        true               | ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
        false              | null
        true               | null
    }

    @Unroll
    def "970 ensure data loader is performant for multiple field with lists using async batch loading"() {

        when:

        batchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(getExpensiveQuery(false))
                .dataLoaderRegistry(dataLoaderRegistry)
                .graphQLContext([(ENABLE_INCREMENTAL_SUPPORT): incrementalSupport])
                .graphQLContext(contextKey == null ? Collections.emptyMap() : [(contextKey): true])
                .build()

        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() <= 2
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() <= 2

        where:
        incrementalSupport | contextKey
        false              | ENABLE_DATA_LOADER_CHAINING
        true               | ENABLE_DATA_LOADER_CHAINING
        false              | ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
        true               | ENABLE_DATA_LOADER_EXHAUSTED_DISPATCHING
        false              | null
        true               | null
    }
}
