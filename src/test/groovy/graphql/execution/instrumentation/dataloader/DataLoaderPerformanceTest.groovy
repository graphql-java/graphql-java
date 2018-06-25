package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.AsyncSerialExecutionStrategy
import graphql.execution.defer.CapturingSubscriber
import graphql.execution.instrumentation.Instrumentation
import org.awaitility.Awaitility
import org.dataloader.DataLoaderRegistry
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getDeferredQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedExpensiveData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedExpensiveDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedListOfDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveDeferredQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.mutation
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.setupGraphQL

class DataLoaderPerformanceTest extends Specification {


    BatchCompareDataFetchers createBatchCompare() {
        return new BatchCompareDataFetchers();
    }

    GraphQL createAsync(BatchCompareDataFetchers batchCompareDataFetchers) {
        DataLoaderPerformanceData dataLoaderPerformanceData = new DataLoaderPerformanceData()
        batchCompareDataFetchers.useAsyncBatchLoading(true)
        DataLoaderRegistry dataLoaderRegistry = dataLoaderPerformanceData.setupDataLoaderRegistry(batchCompareDataFetchers)
        Instrumentation instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry)
        return setupGraphQL(instrumentation, batchCompareDataFetchers)
    }

    GraphQL createAsyncSerial(BatchCompareDataFetchers batchCompareDataFetchers) {
        DataLoaderPerformanceData dataLoaderPerformanceData = new DataLoaderPerformanceData()
        DataLoaderRegistry dataLoaderRegistry = dataLoaderPerformanceData.setupDataLoaderRegistry(batchCompareDataFetchers)
        Instrumentation instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry)
        return setupGraphQL(instrumentation, batchCompareDataFetchers, new AsyncSerialExecutionStrategy())
    }


    @Unroll
    def "760 ensure data loader is performant for lists(strategy: #strategyName)"() {
        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphqlInstance.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        strategyName  | batchCompareDataFetchers | graphqlInstance
        'Async'       | createBatchCompare()     | createAsync(batchCompareDataFetchers)
        'AsyncSerial' | createBatchCompare()     | createAsyncSerial(batchCompareDataFetchers)
    }

    @Unroll
    def "970 ensure data loader is performant for multiple field with lists(strategy: #strategyName)"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveQuery).build()
        def result = graphqlInstance.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        strategyName  | batchCompareDataFetchers | graphqlInstance
        'Async'       | createBatchCompare()     | createAsync(batchCompareDataFetchers)
        'AsyncSerial' | createBatchCompare()     | createAsyncSerial(batchCompareDataFetchers)
    }

    @Unroll
    def "ensure data loader is performant for lists using async batch loading(strategy: #strategyName)"() {

        when:

        batchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphqlInstance.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        strategyName  | batchCompareDataFetchers | graphqlInstance
        'Async'       | createBatchCompare()     | createAsync(batchCompareDataFetchers)
        'AsyncSerial' | createBatchCompare()     | createAsyncSerial(batchCompareDataFetchers)
    }

    @Unroll
    def "970 ensure data loader is performant for multiple field with lists using async batch loading(strategy: #strategyName)"() {

        when:

        batchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveQuery).build()
        def result = graphqlInstance.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        strategyName  | batchCompareDataFetchers | graphqlInstance
        'Async'       | createBatchCompare()     | createAsync(batchCompareDataFetchers)
        'AsyncSerial' | createBatchCompare()     | createAsyncSerial(batchCompareDataFetchers)
    }

    @Unroll
    def "data loader will work with deferred queries(strategy: #strategyName)"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(deferredQuery).build()
        def result = graphqlInstance.execute(executionInput)

        Map<Object, Object> extensions = result.getExtensions()
        Publisher<ExecutionResult> deferredResultStream = (Publisher<ExecutionResult>) extensions.get(GraphQL.DEFERRED_RESULTS)

        def subscriber = new CapturingSubscriber()
        subscriber.subscribeTo(deferredResultStream)
        Awaitility.await().untilTrue(subscriber.finished)


        then:

        result.data == expectedDeferredData

        subscriber.executionResultData == expectedListOfDeferredData

        //
        //  with deferred results, we don't achieve the same efficiency
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3

        where:
        strategyName  | batchCompareDataFetchers | graphqlInstance
        'Async'       | createBatchCompare()     | createAsync(batchCompareDataFetchers)
        'AsyncSerial' | createBatchCompare()     | createAsyncSerial(batchCompareDataFetchers)
    }

    @Unroll
    def "data loader will work with deferred queries on multiple levels deep (strategy: #strategyName)"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveDeferredQuery).build()
        def result = graphqlInstance.execute(executionInput)

        Map<Object, Object> extensions = result.getExtensions()
        Publisher<ExecutionResult> deferredResultStream = (Publisher<ExecutionResult>) extensions.get(GraphQL.DEFERRED_RESULTS)

        def subscriber = new CapturingSubscriber()
        subscriber.subscribeTo(deferredResultStream)
        Awaitility.await().untilTrue(subscriber.finished)


        then:

        result.data == expectedDeferredData

        subscriber.executionResultData == expectedExpensiveDeferredData

        //
        //  with deferred results, we don't achieve the same efficiency
        batchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        batchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3

        where:
        strategyName  | batchCompareDataFetchers | graphqlInstance
        'Async'       | createBatchCompare()     | createAsync(batchCompareDataFetchers)
        'AsyncSerial' | createBatchCompare()     | createAsyncSerial(batchCompareDataFetchers)
    }


    def "data loader will work with mutations"() {
        when:

        def batchCompare = createBatchCompare()
        batchCompare.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(mutation).build()
        def result = createAsync(batchCompare).execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        batchCompare.departmentsForShopsBatchLoaderCounter.get() == 1
        batchCompare.productsForDepartmentsBatchLoaderCounter.get() == 1

    }
}
