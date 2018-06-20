package graphql.execution.instrumentation.dataloader

import graphql.Directives
import graphql.GraphQL
import graphql.execution.instrumentation.Instrumentation
import graphql.schema.GraphQLSchema
import org.dataloader.DataLoaderRegistry


class DataLoaderPerformanceData {

    static DataLoaderRegistry setupDataLoaderRegistry() {
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("departments", BatchCompareDataFetchers.departmentsForShopDataLoader)
        dataLoaderRegistry.register("products", BatchCompareDataFetchers.productsForDepartmentDataLoader)
    }

    static GraphQL setupGraphQL(Instrumentation instrumentation) {
        BatchCompareDataFetchers.resetState()
        GraphQLSchema schema = new BatchCompare().buildDataLoaderSchema()
        schema = schema.transform({ bldr -> bldr.additionalDirective(Directives.DeferDirective) })

        GraphQL.newGraphQL(schema)
                .instrumentation(instrumentation)
                .build()
    }

    static def expectedData = [
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

    static def query = """
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

    static def expectedExpensiveData = [
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


    static def expensiveQuery = """
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

    static def expectedDeferredData = [
            shops: [
                    [id: "shop-1", name: "Shop 1"],
                    [id: "shop-2", name: "Shop 2"],
                    [id: "shop-3", name: "Shop 3"],
            ]
    ]

    static def expectedListOfDeferredData = [
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


    static def deferredQuery = """
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

    static def expensiveDeferredQuery = """
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

    static def expectedExpensiveDeferredData = [
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
}
