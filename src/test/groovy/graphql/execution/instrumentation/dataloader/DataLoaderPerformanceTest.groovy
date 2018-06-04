package graphql.execution.instrumentation.dataloader

import graphql.Directives
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.defer.CapturingSubscriber
import graphql.schema.GraphQLSchema
import org.awaitility.Awaitility
import org.dataloader.DataLoaderRegistry
import org.reactivestreams.Publisher
import spock.lang.Ignore
import spock.lang.Specification

class DataLoaderPerformanceTest extends Specification {

    def expectedData = [
            shops: [
                    [id         : "shop-1", name: "Shop 1",
                     departments: [[id: "department-1", name: "Department 1", products: [[id: "product-1", name: "Product 1"]]],
                                   [id: "department-2", name: "Department 2", products: [[id: "product-2", name: "Product 2"]]],
                                   [id: "department-3", name: "Department 3", products: [[id: "product-3", name: "Product 3"]]]
                     ]],
                    [id         : "shop-2", name: "Shop 2",
                     departments: [[id: "department-4", name: "Department 4", products: [[id: "product-4", name: "Product 4"]]],
                                   [id: "department-5", name: "Department 5", products: [[id: "product-5", name: "Product 5"]]],
                                   [id: "department-6", name: "Department 6", products: [[id: "product-6", name: "Product 6"]]]
                     ]],
                    [id         : "shop-3", name: "Shop 3",
                     departments: [[id: "department-7", name: "Department 7", products: [[id: "product-7", name: "Product 7"]]],
                                   [id: "department-8", name: "Department 8", products: [[id: "product-8", name: "Product 8"]]],
                                   [id: "department-9", name: "Department 9", products: [[id: "product-9", name: "Product 9"]]]]
                    ]]
    ]

    def query = """
            query { 
                shops { 
                    id name 
                    departments { 
                        id name 
                        products { 
                            id name 
                        } 
                    } 
                } 
            }
            """

    def expectedExpensiveData = [
            shops         : [[name                : "Shop 1",
                              departments         : [[name: "Department 1", products: [[name: "Product 1"]], expensiveProducts: [[name: "Product 1"]]],
                                                     [name: "Department 2", products: [[name: "Product 2"]], expensiveProducts: [[name: "Product 2"]]],
                                                     [name: "Department 3", products: [[name: "Product 3"]], expensiveProducts: [[name: "Product 3"]]]],
                              expensiveDepartments: [[name: "Department 1", products: [[name: "Product 1"]], expensiveProducts: [[name: "Product 1"]]],
                                                     [name: "Department 2", products: [[name: "Product 2"]], expensiveProducts: [[name: "Product 2"]]],
                                                     [name: "Department 3", products: [[name: "Product 3"]], expensiveProducts: [[name: "Product 3"]]]]],
                             [name                : "Shop 2",
                              departments         : [[name: "Department 4", products: [[name: "Product 4"]], expensiveProducts: [[name: "Product 4"]]],
                                                     [name: "Department 5", products: [[name: "Product 5"]], expensiveProducts: [[name: "Product 5"]]],
                                                     [name: "Department 6", products: [[name: "Product 6"]], expensiveProducts: [[name: "Product 6"]]]],
                              expensiveDepartments: [[name: "Department 4", products: [[name: "Product 4"]], expensiveProducts: [[name: "Product 4"]]],
                                                     [name: "Department 5", products: [[name: "Product 5"]], expensiveProducts: [[name: "Product 5"]]],
                                                     [name: "Department 6", products: [[name: "Product 6"]], expensiveProducts: [[name: "Product 6"]]]]],
                             [name                : "Shop 3",
                              departments         : [[name: "Department 7", products: [[name: "Product 7"]], expensiveProducts: [[name: "Product 7"]]],
                                                     [name: "Department 8", products: [[name: "Product 8"]], expensiveProducts: [[name: "Product 8"]]],
                                                     [name: "Department 9", products: [[name: "Product 9"]], expensiveProducts: [[name: "Product 9"]]]],
                              expensiveDepartments: [[name: "Department 7", products: [[name: "Product 7"]], expensiveProducts: [[name: "Product 7"]]],
                                                     [name: "Department 8", products: [[name: "Product 8"]], expensiveProducts: [[name: "Product 8"]]],
                                                     [name: "Department 9", products: [[name: "Product 9"]], expensiveProducts: [[name: "Product 9"]]]]]],
            expensiveShops: [[name                : "ExShop 1",
                              departments         : [[name: "Department 1", products: [[name: "Product 1"]], expensiveProducts: [[name: "Product 1"]]],
                                                     [name: "Department 2", products: [[name: "Product 2"]], expensiveProducts: [[name: "Product 2"]]],
                                                     [name: "Department 3", products: [[name: "Product 3"]], expensiveProducts: [[name: "Product 3"]]]],
                              expensiveDepartments: [[name: "Department 1", products: [[name: "Product 1"]], expensiveProducts: [[name: "Product 1"]]],
                                                     [name: "Department 2", products: [[name: "Product 2"]], expensiveProducts: [[name: "Product 2"]]],
                                                     [name: "Department 3", products: [[name: "Product 3"]], expensiveProducts: [[name: "Product 3"]]]]],
                             [name                : "ExShop 2",
                              departments         : [[name: "Department 4", products: [[name: "Product 4"]], expensiveProducts: [[name: "Product 4"]]],
                                                     [name: "Department 5", products: [[name: "Product 5"]], expensiveProducts: [[name: "Product 5"]]],
                                                     [name: "Department 6", products: [[name: "Product 6"]], expensiveProducts: [[name: "Product 6"]]]],
                              expensiveDepartments: [[name: "Department 4", products: [[name: "Product 4"]], expensiveProducts: [[name: "Product 4"]]],
                                                     [name: "Department 5", products: [[name: "Product 5"]], expensiveProducts: [[name: "Product 5"]]],
                                                     [name: "Department 6", products: [[name: "Product 6"]], expensiveProducts: [[name: "Product 6"]]]]],
                             [name                : "ExShop 3",
                              departments         : [[name: "Department 7", products: [[name: "Product 7"]], expensiveProducts: [[name: "Product 7"]]],
                                                     [name: "Department 8", products: [[name: "Product 8"]], expensiveProducts: [[name: "Product 8"]]],
                                                     [name: "Department 9", products: [[name: "Product 9"]], expensiveProducts: [[name: "Product 9"]]]],
                              expensiveDepartments: [[name: "Department 7", products: [[name: "Product 7"]], expensiveProducts: [[name: "Product 7"]]],
                                                     [name: "Department 8", products: [[name: "Product 8"]], expensiveProducts: [[name: "Product 8"]]],
                                                     [name: "Department 9", products: [[name: "Product 9"]], expensiveProducts: [[name: "Product 9"]]]]]]

    ]


    def expensiveQuery = """
            query { 
                shops { 
                    name 
                    departments { 
                        name 
                        products { 
                            name 
                        } 
                        expensiveProducts { 
                            name 
                        } 
                    } 
                    expensiveDepartments { 
                        name 
                        products { 
                            name 
                        } 
                        expensiveProducts { 
                            name 
                        } 
                    } 
                } 
                expensiveShops { 
                    name 
                    departments { 
                        name 
                        products { 
                            name 
                        } 
                        expensiveProducts { 
                            name 
                        } 
                    } 
                    expensiveDepartments { 
                        name 
                        products { 
                            name 
                        } 
                        expensiveProducts { 
                            name 
                        } 
                    } 
                } 
            }
            """

    GraphQL graphQL

    void setup() {
        BatchCompareDataFetchers.resetState()
        GraphQLSchema schema = new BatchCompare().buildDataLoaderSchema()
        schema = schema.transform({ bldr -> bldr.additionalDirective(Directives.DeferDirective) })

        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("departments", BatchCompareDataFetchers.departmentsForShopDataLoader)
        dataLoaderRegistry.register("products", BatchCompareDataFetchers.productsForDepartmentDataLoader)
        def instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry)

        graphQL = GraphQL
                .newGraphQL(schema)
                .instrumentation(instrumentation)
                .build()
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

    @Ignore("we still have problems with async data loading locking up")
    def "ensure data loader is performant for lists using async batch loading"() {

        when:

        BatchCompareDataFetchers.useAsyncDataLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

    }

    @Ignore
    def "970 ensure data loader is performant for multiple field with lists using async data loading"() {

        when:

        BatchCompareDataFetchers.useAsyncDataLoading(true)

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveQuery).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedExpensiveData

        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1
    }

    def expectedDeferredData = [
            shops: [
                    [id: "shop-1", name: "Shop 1"],
                    [id: "shop-2", name: "Shop 2"],
                    [id: "shop-3", name: "Shop 3"],
            ]
    ]

    def expectedListOfDeferredData = [
            [[id: "department-1", name: "Department 1", products: [[id: "product-1", name: "Product 1"]]],
             [id: "department-2", name: "Department 2", products: [[id: "product-2", name: "Product 2"]]],
             [id: "department-3", name: "Department 3", products: [[id: "product-3", name: "Product 3"]]]]
            ,

            [[id: "department-4", name: "Department 4", products: [[id: "product-4", name: "Product 4"]]],
             [id: "department-5", name: "Department 5", products: [[id: "product-5", name: "Product 5"]]],
             [id: "department-6", name: "Department 6", products: [[id: "product-6", name: "Product 6"]]]]
            ,
            [[id: "department-7", name: "Department 7", products: [[id: "product-7", name: "Product 7"]]],
             [id: "department-8", name: "Department 8", products: [[id: "product-8", name: "Product 8"]]],
             [id: "department-9", name: "Department 9", products: [[id: "product-9", name: "Product 9"]]]]
            ,

    ]


    def deferredQuery = """
            query { 
                shops { 
                    id name 
                    departments @defer { 
                        id name 
                        products { 
                            id name 
                        } 
                    } 
                } 
            }
            """

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

    def expensiveDeferredQuery = """
            query { 
                shops { 
                    id name 
                    departments @defer { 
                        name 
                        products @defer { 
                            name 
                        } 
                        expensiveProducts @defer { 
                            name 
                        } 
                    } 
                    expensiveDepartments @defer { 
                        name 
                        products { 
                            name 
                        } 
                        expensiveProducts { 
                            name 
                        } 
                    } 
                } 
                expensiveShops @defer { 
                    id name
                } 
            }
            """

    def expectedExpensiveDeferredData = [
            [[id: "exshop-1", name: "ExShop 1"], [id: "exshop-2", name: "ExShop 2"], [id: "exshop-3", name: "ExShop 3"]],
            [[name: "Department 1"], [name: "Department 2"], [name: "Department 3"]],
            [[name: "Department 1", products: [[name: "Product 1"]], expensiveProducts: [[name: "Product 1"]]], [name: "Department 2", products: [[name: "Product 2"]], expensiveProducts: [[name: "Product 2"]]], [name: "Department 3", products: [[name: "Product 3"]], expensiveProducts: [[name: "Product 3"]]]],
            [[name: "Department 4"], [name: "Department 5"], [name: "Department 6"]],
            [[name: "Department 4", products: [[name: "Product 4"]], expensiveProducts: [[name: "Product 4"]]], [name: "Department 5", products: [[name: "Product 5"]], expensiveProducts: [[name: "Product 5"]]], [name: "Department 6", products: [[name: "Product 6"]], expensiveProducts: [[name: "Product 6"]]]],
            [[name: "Department 7"], [name: "Department 8"], [name: "Department 9"]],
            [[name: "Department 7", products: [[name: "Product 7"]], expensiveProducts: [[name: "Product 7"]]], [name: "Department 8", products: [[name: "Product 8"]], expensiveProducts: [[name: "Product 8"]]], [name: "Department 9", products: [[name: "Product 9"]], expensiveProducts: [[name: "Product 9"]]]],
            [[name: "Product 1"]],
            [[name: "Product 1"]],
            [[name: "Product 2"]],
            [[name: "Product 2"]],
            [[name: "Product 3"]],
            [[name: "Product 3"]],
            [[name: "Product 4"]],
            [[name: "Product 4"]],
            [[name: "Product 5"]],
            [[name: "Product 5"]],
            [[name: "Product 6"]],
            [[name: "Product 6"]],
            [[name: "Product 7"]],
            [[name: "Product 7"]],
            [[name: "Product 8"]],
            [[name: "Product 8"]],
            [[name: "Product 9"]],
            [[name: "Product 9"]],
    ]

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
