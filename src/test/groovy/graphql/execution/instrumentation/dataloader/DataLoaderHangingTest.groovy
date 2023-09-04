package graphql.execution.instrumentation.dataloader

import com.github.javafaker.Faker
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.Async
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.instrumentation.dataloader.models.Company
import graphql.execution.instrumentation.dataloader.models.Person
import graphql.execution.instrumentation.dataloader.models.Product
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderOptions
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

import static graphql.ExecutionInput.newExecutionInput
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class DataLoaderHangingTest extends Specification {

    public static final int NUM_OF_REPS = 50

    def "deadlock attempt"() {
        setup:
        def sdl = """
        type Album {
            id: ID!
            title: String!
            artist: Artist
            songs(
                limit: Int,
                nextToken: String
            ): ModelSongConnection
        }

        type Artist {
            id: ID!
            name: String!
            albums(
                limit: Int,
                nextToken: String
            ): ModelAlbumConnection
            songs(
                limit: Int,
                nextToken: String
            ): ModelSongConnection
        }

        type ModelAlbumConnection {
            items: [Album]
            nextToken: String
        }

        type ModelArtistConnection {
            items: [Artist]
            nextToken: String
        }

        type ModelSongConnection {
            items: [Song]
            nextToken: String
        }

        type Query {
            listArtists(limit: Int, nextToken: String): ModelArtistConnection
        }

        type Song {
            id: ID!
            title: String!
            artist: Artist
            album: Album
        }
        """

        ThreadFactory threadFactory = new BasicThreadFactory.Builder()
                .namingPattern("resolver-chain-thread-%d").build()
        def executor = new ThreadPoolExecutor(15, 15, 0L,
                TimeUnit.MILLISECONDS, new SynchronousQueue<>(), threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy())

        DataFetcher albumsDf = { env -> env.getDataLoader("artist.albums").load(env) }
        DataFetcher songsDf = { env -> env.getDataLoader("album.songs").load(env) }

        def dataFetcherArtists = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                def limit = environment.getArgument("limit") as Integer
                def artists = []
                for (int i = 1; i <= limit; i++) {
                    artists.add(['id': "artist-$i", 'name': "artist-$i"])
                }
                return ['nextToken': 'artist-next', 'items': artists]
            }
        }

        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("listArtists", dataFetcherArtists))
                .type(newTypeWiring("Artist")
                        .dataFetcher("albums", albumsDf))
                .type(newTypeWiring("Album")
                        .dataFetcher("songs", songsDf))
                .build()

        def schema = TestUtil.schema(sdl, wiring)

        when:
        def graphql = GraphQL.newGraphQL(schema)
                .instrumentation(new DataLoaderDispatcherInstrumentation())
                .build()

        then: "execution shouldn't hang"
        def futures = Async.ofExpectedSize(NUM_OF_REPS)
        for (int i = 0; i < NUM_OF_REPS; i++) {
            DataLoaderRegistry dataLoaderRegistry = mkNewDataLoaderRegistry(executor)

            def result = graphql.executeAsync(newExecutionInput()
                    .dataLoaderRegistry(dataLoaderRegistry)
                    .query("""
                    query getArtistsWithData {
                      listArtists(limit: 1) {
                        items {
                          name
                          albums(limit: 200) {
                            items {
                              title
                              # Uncommenting the following causes query to timeout
                              songs(limit: 5) {
                                 nextToken
                                 items {
                                   title
                                 }
                              }
                            }
                          }
                        }
                      }
                    }
                        """)
                    .build())
            result.whenComplete({ res, error ->
                if (error) {
                    throw error
                }
                assert res.errors.empty
            })
            // add all futures
            futures.add(result)
        }
        // wait for each future to complete and grab the results
        futures.await()
                .whenComplete({ results, error ->
                    if (error) {
                        throw error
                    }
                    results.each { assert it.errors.empty }
                })
                .join()
    }

    private DataLoaderRegistry mkNewDataLoaderRegistry(executor) {
        def dataLoaderAlbums = DataLoaderFactory.newDataLoader(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
            @Override
            CompletionStage<List<List<Object>>> load(List<DataFetchingEnvironment> keys) {
                return CompletableFuture.supplyAsync({
                    def limit = keys.first().getArgument("limit") as Integer
                    return keys.collect({ k ->
                        def albums = []
                        for (int i = 1; i <= limit; i++) {
                            albums.add(['id': "artist-$k.source.id-$i", 'title': "album-$i"])
                        }
                        def albumsConnection = ['nextToken': 'album-next', 'items': albums]
                        return albumsConnection
                    })
                }, executor)
            }
        }, DataLoaderOptions.newOptions().setMaxBatchSize(5))

        def dataLoaderSongs = DataLoaderFactory.newDataLoader(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
            @Override
            CompletionStage<List<List<Object>>> load(List<DataFetchingEnvironment> keys) {
                return CompletableFuture.supplyAsync({
                    def limit = keys.first().getArgument("limit") as Integer
                    return keys.collect({ k ->
                        def songs = []
                        for (int i = 1; i <= limit; i++) {
                            songs.add(['id': "album-$k.source.id-$i", 'title': "song-$i"])
                        }
                        def songsConnection = ['nextToken': 'song-next', 'items': songs]
                        return songsConnection
                    })
                }, executor)
            }
        }, DataLoaderOptions.newOptions().setMaxBatchSize(5))

        def dataLoaderRegistry = new DataLoaderRegistry()
        dataLoaderRegistry.register("artist.albums", dataLoaderAlbums)
        dataLoaderRegistry.register("album.songs", dataLoaderSongs)
        dataLoaderRegistry
    }

    /*
      Test code taken from https://github.com/graphql-java/graphql-java/issues/1973
     */
    Faker faker = new Faker()
    String schema = """
        type Company { 
           id: Int!
        } 
        type Person {
          company: Company! 
        } 
        type Product { 
          suppliedBy: Person!
        } 
        type QueryType { 
          products: [Product!]! 
        } 
        schema { 
          query: QueryType 
        }
        """

    DataFetcherExceptionHandler customExceptionHandlerThatThrows = new DataFetcherExceptionHandler() {

        @Override
        CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters handlerParameters) {
            //
            // this is a weird test case - its not actually handling the exception - its a test
            // case where the handler code itself throws an exception during the handling
            // and that will not stop the DataLoader from being dispatched
            throw handlerParameters.exception
        }
    }

    BatchLoader<Integer, Person> personBatchLoader = new BatchLoader<Integer, Person>() {
        @Override
        CompletionStage<List<Person>> load(List<Integer> keys) {
            return CompletableFuture.supplyAsync({
                List<Person> people = keys.stream()
                        .map({ id -> new Person(id, faker.name().fullName(), id + 200) })
                        .collect(Collectors.toList())
                return people
            })
        }
    }

    BatchLoader<Integer, Company> companyBatchLoader = new BatchLoader<Integer, Company>() {
        @Override
        CompletionStage<List<Company>> load(List<Integer> keys) {
            return CompletableFuture.supplyAsync({
                def companies = keys.stream()
                        .map({ id -> new Company(id, faker.company().name()) })
                        .collect(Collectors.toList())
                return companies
            })
        }
    }

    // Always returns exactly 2 products, one supplied by person with ID #0 and one supplied by person with ID #1.
    DataFetcher productsDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            List<Product> products = new ArrayList<>()
            for (int id = 0; id < 2; id++) {
                products.add(new Product(faker.idNumber().toString(), faker.commerce().productName(), id, []))
            }
            return products
        }
    }

    // Loads the person pointed to via Product.getSuppliedById.
    // Then return it, unless the person has ID 0 in which case it fails.
    DataFetcher suppliedByDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            Product source = environment.getSource()
            DataLoaderRegistry dlRegistry = environment.getGraphQlContext().get("registry")
            DataLoader<Integer, Person> personDL = dlRegistry.getDataLoader("person")
            return personDL.load(source.getSuppliedById()).thenApply({ person ->
                if (person.id == 0) {
                    throw new RuntimeException("Failure in suppliedByDF for person with ID == 0.")
                }
                return person
            })
        }
    }

    DataFetcher companyDF = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            Person source = environment.getSource()
            DataLoaderRegistry dlRegistry = environment.getGraphQlContext().get("registry")
            DataLoader<Integer, Company> companyDL = dlRegistry.getDataLoader("company")
            return companyDL.load(source.getCompanyId())
        }
    }

    RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type("QueryType", { builder -> builder.dataFetcher("products", productsDF) })
            .type("Product", { builder -> builder.dataFetcher("suppliedBy", suppliedByDF) })
            .type("Person", { builder -> builder.dataFetcher("company", companyDF) })
            .build()


    def graphQLSchema = TestUtil.schema(schema, runtimeWiring)

    def query = """
        query Products { 
            products {
                suppliedBy {
                    company { 
                        id 
                    } 
                }
            } 
        }
        """

    private DataLoaderRegistry buildRegistry() {
        DataLoader<Integer, Person> personDataLoader = DataLoaderFactory.newDataLoader(personBatchLoader)
        DataLoader<Integer, Company> companyDataLoader = DataLoaderFactory.newDataLoader(companyBatchLoader)

        DataLoaderRegistry registry = new DataLoaderRegistry()
        registry.register("person", personDataLoader)
        registry.register("company", companyDataLoader)
        return registry
    }

    def "execution should never hang even if the datafetcher of one object in a list fails"() {

        DataLoaderRegistry registry = buildRegistry()

        GraphQL graphQL = GraphQL
                .newGraphQL(graphQLSchema)
                .queryExecutionStrategy(new AsyncExecutionStrategy(customExceptionHandlerThatThrows))
                .instrumentation(new DataLoaderDispatcherInstrumentation())
                .build()

        when:

        ExecutionInput executionInput = newExecutionInput()
                .query(query)
                .graphQLContext(["registry": registry])
                .dataLoaderRegistry(registry)
                .build()

        def executionResult = graphQL.execute(executionInput)


        then:

        (executionResult.errors.size() > 0)
    }
}