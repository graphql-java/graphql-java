package graphql.execution.instrumentation.dataloader

import com.github.javafaker.Faker
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.instrumentation.dataloader.models.Company
import graphql.execution.instrumentation.dataloader.models.Person
import graphql.execution.instrumentation.dataloader.models.Product
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.stream.Collectors
import java.util.stream.IntStream

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring

class PeopleCompaniesAndProductsDataLoaderTest extends Specification {

    int personBatchLoadInvocationCount = 0
    int personBatchLoadKeyCount = 0
    int companyBatchLoadInvocationCount = 0
    int companyBatchLoadKeyCount = 0

    void setup() {
        personBatchLoadInvocationCount = 0
        personBatchLoadKeyCount = 0
        companyBatchLoadInvocationCount = 0
        companyBatchLoadKeyCount = 0
    }

    Faker faker = new Faker()
    String schema = """
        type Company { 
           id: Int! 
           name: String! 
        } 
        type Person { 
          id: Int! 
          name: String! 
          company: Company! 
        } 
        type Product { 
          id: Int! 
          name: String! 
          suppliedBy: Person!
          madeBy: [Person] 
        } 
        type QueryType { 
          products: [Product!]! 
        } 
        schema { 
          query: QueryType 
        }
        """


    BatchLoader<Integer, Person> personBatchLoader = new BatchLoader<Integer, Person>() {
        @Override
        CompletionStage<List<Person>> load(List<Integer> keys) {
            personBatchLoadInvocationCount += 1
            personBatchLoadKeyCount += keys.size()
            CompletableFuture.supplyAsync({

                keys.stream()
                        .map({ id -> new Person(id, faker.name().fullName(), id + 200) })
                        .collect(Collectors.toList())
            })
        }
    }

    BatchLoader<Integer, Company> companyBatchLoader = new BatchLoader<Integer, Company>() {
        @Override
        CompletionStage<List<Company>> load(List<Integer> keys) {
            companyBatchLoadInvocationCount += 1
            companyBatchLoadKeyCount += keys.size()
            CompletableFuture.supplyAsync({
                return keys.stream()
                        .map({ id -> new Company(id, faker.company().name()) })
                        .collect(Collectors.toList())
            })
        }
    }

    DataFetcher productsDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            return IntStream.range(0, 5)
                    .mapToObj(
                    { id ->
                        List<Integer> madeBy = [id * 10001, id * 10002, id * 10003, id * 10004, id * 10005]
                        new Product(id.toString(), faker.commerce().productName(), id + 200, madeBy)
                    })
                    .collect(Collectors.toList())
        }
    }

    DataFetcher suppliedByDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            Product source = environment.getSource()
            DataLoaderRegistry dlRegistry = environment.getContext()
            DataLoader<Integer, Person> personDL = dlRegistry.getDataLoader("person")
            return personDL.load(source.getSuppliedById())
        }
    }

    DataFetcher madeByDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            Product source = environment.getSource()
            DataLoaderRegistry dlRegistry = environment.getContext()
            DataLoader<Integer, Person> personDL = dlRegistry.getDataLoader("person")

            return personDL.loadMany(source.getMadeByIds())
        }
    }

    DataFetcher companyDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            Person source = environment.getSource()
            DataLoaderRegistry dlRegistry = environment.getContext()
            DataLoader<Integer, Company> companyDL = dlRegistry.getDataLoader("company")
            return companyDL.load(source.getCompanyId())
        }
    }

    RuntimeWiring runtimeWiring = newRuntimeWiring()
            .type("QueryType", { builder -> builder.dataFetcher("products", productsDF) })
            .type("Product", { builder -> builder.dataFetcher("suppliedBy", suppliedByDF) })
            .type("Product", { builder -> builder.dataFetcher("madeBy", madeByDF) })
            .type("Person", { builder -> builder.dataFetcher("company", companyDF) })
            .build()


    def graphQLSchema = TestUtil.schema(schema, runtimeWiring)

    def query = """
        query Products { 
            products { 
                id 
                name 
                suppliedBy { 
                    id 
                    name 
                    company { 
                        id 
                        name 
                    } 
                } 
                madeBy { 
                    id 
                    name 
                    company { 
                        id 
                        name 
                    } 
                } 
            } 
        }
        """


    private DataLoaderRegistry buildRegistry() {
        DataLoader<Integer, Person> personDataLoader = new DataLoader<>(personBatchLoader)
        DataLoader<Integer, Company> companyDataLoader = new DataLoader<>(companyBatchLoader)

        DataLoaderRegistry registry = new DataLoaderRegistry()
        registry.register("person", personDataLoader)
        registry.register("company", companyDataLoader)
        return registry
    }

    def "ensure performant loading with field tracking"() {

        DataLoaderRegistry registry = buildRegistry()

        GraphQL graphQL = GraphQL
                .newGraphQL(graphQLSchema)
                .instrumentation(new DataLoaderDispatcherInstrumentation(registry))
                .build()

        when:

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(query)
                .context(registry)
                .build()

        def executionResult = graphQL.execute(executionInput)


        then:

        (executionResult.data["products"] as Map).size() == 5

        personBatchLoadKeyCount == 26
        personBatchLoadInvocationCount == 1

        companyBatchLoadKeyCount == 26

        companyBatchLoadInvocationCount == 1

    }
}
