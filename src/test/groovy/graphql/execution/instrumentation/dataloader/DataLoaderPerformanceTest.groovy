package graphql.execution.instrumentation.dataloader

import graphql.Directives
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import org.awaitility.Awaitility
import org.dataloader.DataLoaderRegistry
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicBoolean

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

    void setup() {
        BatchCompareDataFetchers.resetState()
    }

    @Unroll
    def "760 ensure data loader is performant for lists with (approach: #approachName)"() {

        when:

        GraphQLSchema schema = new BatchCompare().buildDataLoaderSchema()
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("departments", BatchCompareDataFetchers.departmentsForShopDataLoader)
        dataLoaderRegistry.register("products", BatchCompareDataFetchers.productsForDepartmentDataLoader)
        def options = DataLoaderDispatcherInstrumentationOptions.newOptions().useCombinedCallsApproach(approachFlag)
        def instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry, options)
        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                .instrumentation(instrumentation)
                .build()
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        //  eg 1 for shops-->departments and one for departments --> products
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        where:
        approachName    | approachFlag
        "CombinedCalls" | true
        "FieldTracking" | false
    }

    def "970 ensure data loader is performant for multiple field with lists with (approach: #approachName)"() {

        when:

        GraphQLSchema schema = new BatchCompare().buildDataLoaderSchema()
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("departments", BatchCompareDataFetchers.departmentsForShopDataLoader)
        dataLoaderRegistry.register("products", BatchCompareDataFetchers.productsForDepartmentDataLoader)
        def options = DataLoaderDispatcherInstrumentationOptions.newOptions().useCombinedCallsApproach(false)
        def instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry, options)
        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                .instrumentation(instrumentation)
                .build()
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(expensiveQuery).build()
        def result = graphQL.execute(executionInput)

        then:
//        result.data == expectedExpensiveData
        //
        //  ideally 1 for shops-->departments and one for departments --> products but currently not the case
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 1

        assertMapEqualsWithoutListOrder(result.data, expectedExpensiveData)

//        where:
//        approachName    | approachFlag
////        "CombinedCalls" | true
//        "FieldTracking" | false
    }

    boolean assertMapEqualsWithoutListOrder(Map map1, Map map2) {
        def modifiedMap1 = replaceListWithSet(map1)
        def modifiedMap2 = replaceListWithSet(map2)
        assert modifiedMap1 == modifiedMap2
        return true
    }

    Map replaceListWithSet(Map map) {
        map.collectEntries { k, v ->
            if (v instanceof List) {
                [k, new LinkedHashSet<>(v)]
            } else if (v instanceof Map) {
                [k, replaceListWithSet(v)]
            } else {
                [k, v]
            }
        }
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

    @Unroll
    def "data loader will work with deferred queries with (approach: #approachName)"() {

        when:

        GraphQLSchema schema = new BatchCompare().buildDataLoaderSchema().transform({
            it.additionalDirective(Directives.DeferDirective)
        })
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("departments", BatchCompareDataFetchers.departmentsForShopDataLoader)
        dataLoaderRegistry.register("products", BatchCompareDataFetchers.productsForDepartmentDataLoader)
        def instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry)
        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                .instrumentation(instrumentation)
                .build()
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(deferredQuery).build()
        def result = graphQL.execute(executionInput)

        then:

        Map<Object, Object> extensions = result.getExtensions()
        Publisher<ExecutionResult> deferredResults = (Publisher<ExecutionResult>) extensions.get(GraphQL.DEFERRED_RESULTS)

        def done = new AtomicBoolean()
        def results = []
        deferredResults.subscribe(new Subscriber<ExecutionResult>() {

            Subscription subscription

            @Override
            void onSubscribe(Subscription s) {
                subscription = s
                subscription.request(10)
            }

            @Override
            void onNext(ExecutionResult executionResult) {
                assert executionResult.errors.isEmpty(), "We don't expect graphql errors"
                results.add(executionResult.data)
                subscription.request(10)
            }

            @Override
            void onError(Throwable t) {
                assert false, "This should not happen"
                done.set(true)
            }

            @Override
            void onComplete() {
                done.set(true)
            }
        })

        Awaitility.await().untilTrue(done)

        result.data == expectedDeferredData

        results == expectedListOfDeferredData

        //
        //  with deferred results, we don't achieve the same efficiency
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3

        where:
        approachName    | approachFlag
        "CombinedCalls" | true
        "FieldTracking" | false

    }
}
