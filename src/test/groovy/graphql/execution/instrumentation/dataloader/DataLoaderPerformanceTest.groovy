package graphql.execution.instrumentation.dataloader

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters
import graphql.schema.GraphQLSchema
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

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

    void setup() {
        BatchCompareDataFetchers.resetState()
    }

    def "760 ensure data loader is performant for lists"() {

        when:

        GraphQLSchema schema = new BatchCompare().buildDataLoaderSchema()
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("departments", BatchCompareDataFetchers.departmentsForShopDataLoader)
        dataLoaderRegistry.register("products", BatchCompareDataFetchers.productsForDepartmentDataLoader)
        def instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry)
        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                .instrumentation(instrumentation)
                .build()
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData
        //
        // 1 per level, eg 1 for shops-->departments and one for departments --> products
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 1
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 3
    }

    def "760 when list handling is missing, its less efficient"() {

        when:

        GraphQLSchema schema = new BatchCompare().buildDataLoaderSchema()
        DataLoaderRegistry dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("departments", BatchCompareDataFetchers.departmentsForShopDataLoader)
        dataLoaderRegistry.register("products", BatchCompareDataFetchers.productsForDepartmentDataLoader)
        def instrumentation = new DataLoaderDispatcherInstrumentation(dataLoaderRegistry) {
            @Override
            InstrumentationContext<CompletableFuture<ExecutionResult>> beginCompleteFieldList(InstrumentationFieldCompleteParameters parameters) {
                // if we never call super.xxx() then it wont record we are in a list and it wont be efficient
                return { e, t -> }
            }
        }
        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                .instrumentation(instrumentation)
                .build()
        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query(query).build()
        def result = graphQL.execute(executionInput)

        then:
        result.data == expectedData

        //
        // notice how its much less efficient because the "list handling" is causing eager
        // data loader dispatches
        //
        BatchCompareDataFetchers.departmentsForShopsBatchLoaderCounter.get() == 3
        BatchCompareDataFetchers.productsForDepartmentsBatchLoaderCounter.get() == 9
    }

}
