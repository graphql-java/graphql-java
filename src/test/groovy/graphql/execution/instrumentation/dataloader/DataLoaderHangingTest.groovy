package graphql.execution.instrumentation.dataloader

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.Async
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderOptions
import org.dataloader.DataLoaderRegistry
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

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
        List<CompletableFuture<ExecutionResult>> futures = []
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
        Async.each(futures)
                .whenComplete({ results, error ->
            if (error) {
                throw error
            }
            results.each { assert it.errors.empty }
        })
                .join()
    }

    private DataLoaderRegistry mkNewDataLoaderRegistry(executor) {
        def dataLoaderAlbums = new DataLoader<Object, Object>(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
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

        def dataLoaderSongs = new DataLoader<Object, Object>(new BatchLoader<DataFetchingEnvironment, List<Object>>() {
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
}