package graphql.execution.instrumentation.dataloader


import graphql.ExecutionInput
import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import org.dataloader.DataLoaderRegistry
import spock.lang.Ignore
import spock.lang.Specification

import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedExpensiveData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getQuery

class DataLoaderPerformanceWithChainedInstrumentationTest extends Specification {

    GraphQL graphQL
    DataLoaderRegistry dataLoaderRegistry
    BatchCompareDataFetchers batchCompareDataFetchers


    void setup() {
        batchCompareDataFetchers = new BatchCompareDataFetchers()
        DataLoaderPerformanceData dataLoaderPerformanceData = new DataLoaderPerformanceData(batchCompareDataFetchers)

        dataLoaderRegistry = dataLoaderPerformanceData.setupDataLoaderRegistry()
        Instrumentation instrumentation = new ChainedInstrumentation(
                Collections.singletonList(new DataLoaderDispatcherInstrumentation()))
        graphQL = dataLoaderPerformanceData.setupGraphQL(instrumentation)
    }

    def "chainedInstrumentation: 760 ensure data loader is performant for lists"() {
        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).dataLoaderRegistry(dataLoaderRegistry).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1
    }

    @Ignore("This test flakes on Travis for some reason.  Clearly this indicates some sort of problem to investigate.  However it also stop releases.")
    def "chainedInstrumentation: 970 ensure data loader is performant for multiple field with lists"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveQuery).dataLoaderRegistry(dataLoaderRegistry).build()

        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

    }

    def "chainedInstrumentation: ensure data loader is performant for lists using async batch loading"() {

        when:

        batchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).dataLoaderRegistry(dataLoaderRegistry).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

    }

    def "chainedInstrumentation: 970 ensure data loader is performant for multiple field with lists using async batch loading"() {

        when:

        batchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveQuery).dataLoaderRegistry(dataLoaderRegistry).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() <= 2
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() <= 2
    }


}
