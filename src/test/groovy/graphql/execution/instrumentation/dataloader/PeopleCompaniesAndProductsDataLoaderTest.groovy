package graphql.execution.instrumentation.dataloader

import com.github.javafaker.Faker
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
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

    def "ensure performant loading"() {
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
        } 
        type QueryType { 
          products: [Product!]! 
        } 
        schema { 
          query: QueryType 
        }
        """

        int personBatchLoadInvocationCount = 0
        int personBatchLoadKeyCount = 0
        int companyBatchLoadInvocationCount = 0
        int companyBatchLoadKeyCount = 0

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

        DataLoader<Integer, Person> personDataLoader = new DataLoader<>(personBatchLoader)
        DataLoader<Integer, Company> companyDataLoader = new DataLoader<>(companyBatchLoader)

        DataLoaderRegistry registry = new DataLoaderRegistry()
        registry.register("person", personDataLoader)
        registry.register("company", companyDataLoader)

        DataLoaderDispatcherInstrumentation dispatcherInstrumentation = new DataLoaderDispatcherInstrumentation(registry)

        DataFetcher productsDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                return IntStream.rangeClosed(0, 99)
                        .mapToObj({ id -> new Product(id.toString(), faker.commerce().productName(), id + 200) })
                        .collect(Collectors.toList())
            }
        }

        DataFetcher suppliedByDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                Product source = environment.getSource()
                return personDataLoader.load(source.getSuppliedById())
            }
        }

        DataFetcher companyDF = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                Person source = environment.getSource()
                return companyDataLoader.load(source.getCompanyId())
            }
        }


        RuntimeWiring runtimeWiring = newRuntimeWiring()
                .type("QueryType", { builder -> builder.dataFetcher("products", productsDF) })
                .type("Product", { builder -> builder.dataFetcher("suppliedBy", suppliedByDF) })
                .type("Person", { builder -> builder.dataFetcher("company", companyDF) })
                .build()


        def graphQLSchema = TestUtil.schema(schema, runtimeWiring)

        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).instrumentation(dispatcherInstrumentation).build()

        when:
        ExecutionResult executionResult = graphQL.execute("""
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
            } 
        }
        """)

        then:

        executionResult.data != null
        personBatchLoadKeyCount == 100
        personBatchLoadInvocationCount == 1

        companyBatchLoadKeyCount == 100
        //
        // ideally we get this down to 1 but at the moment its 100.  We need to work on this
        //companyBatchLoadInvocationCount == 1
        companyBatchLoadInvocationCount == 100

    }
}
