package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.defer.CapturingSubscriber
import graphql.execution.instrumentation.Instrumentation
import org.awaitility.Awaitility
import org.dataloader.DataLoaderRegistry
import org.reactivestreams.Publisher
import spock.lang.Specification

import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getDeferredQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedExpensiveData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedExpensiveDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpectedListOfDeferredData
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveDeferredQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getExpensiveQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.getQuery
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.setupDataLoaderRegistry
import static graphql.execution.instrumentation.dataloader.DataLoaderPerformanceData.setupGraphQL

class DataLoaderPerformanceTest extends Specification {

    GraphQL graphQL

    void setup() {
        DataLoaderRegistry dataLoaderRegistry = setupDataLoaderRegistry()
        Instrumentation instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry)
        graphQL = setupGraphQL(instrumentation)
    }

    def "760 ensure data loader is performant for lists"() {
        when:
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1
    }

    def "970 ensure data loader is performant for multiple field with lists"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveQuery).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1
    }

    def "ensure data loader is performant for lists using async batch loading"() {

        when:

        BatchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

    }

    def "970 ensure data loader is performant for multiple field with lists using async batch loading"() {

        when:

        BatchCompareDataFetchers.useAsyncBatchLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveQuery).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1
    }

    def "data loader will work with deferred queries"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(deferredQuery).build()
        def result = graphQL.execute(executionInput)

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
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3
    }

    def "data loader will work with deferred queries on multiple levels deep"() {

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveDeferredQuery).build()
        def result = graphQL.execute(executionInput)

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
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3
    }
}
