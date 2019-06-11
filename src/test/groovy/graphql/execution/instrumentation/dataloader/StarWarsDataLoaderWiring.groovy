package graphql.execution.instrumentation.dataloader

import graphql.StarWarsData
import graphql.TypeResolutionEnvironment
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import graphql.schema.idl.MapEnumValuesProvider
import graphql.schema.idl.RuntimeWiring
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.stream.Collectors

import static graphql.TestUtil.schemaFile
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

/**
 * A helper class that contains the complex wiring used in other tests
 */
class StarWarsDataLoaderWiring {
    int rawCharacterLoadCount = 0
    int batchFunctionLoadCount = 0
    int naiveLoadCount = 0

    Object getCharacterData(String id) {
        rawCharacterLoadCount++
        if (StarWarsData.humanData[id] != null) return StarWarsData.humanData[id]
        if (StarWarsData.droidData[id] != null) return StarWarsData.droidData[id]
        return null
    }

    List<Object> getCharacterDataViaBatchHTTPApi(List<String> keys) {
        keys.stream().map({ id -> getCharacterData(id) }).collect(Collectors.toList())
    }

    // a batch loader function that will be called with N or more keys for batch loading
    BatchLoader<String, Object> characterBatchLoader = new BatchLoader<String, Object>() {
        @Override
        CompletionStage<List<Object>> load(List<String> keys) {
            batchFunctionLoadCount++

            //
            // direct return of values
            //CompletableFuture.completedFuture(getCharacterDataViaBatchHTTPApi(keys))
            //
            // or
            //
            // async supply of values
            CompletableFuture.supplyAsync({
                return getCharacterDataViaBatchHTTPApi(keys)
            })
        }

    }

    def newDataLoaderRegistry() {
        // a data loader for characters that points to the character batch loader
        def characterDataLoader = new DataLoader<String, Object>(characterBatchLoader)
        new DataLoaderRegistry().register("character", characterDataLoader)
    }

    // we define the normal StarWars data fetchers so we can point them at our data loader
    DataFetcher humanDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            String id = environment.arguments.id
            naiveLoadCount += 1
            environment.getDataLoader("character").load(id)
        }
    }


    DataFetcher droidDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            String id = environment.arguments.id
            naiveLoadCount += 1
            environment.getDataLoader("character").load(id)
        }
    }

    DataFetcher heroDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            naiveLoadCount += 1
            environment.getDataLoader("character").load('2001') // R2D2
        }
    }

    DataFetcher friendsDataFetcher = new DataFetcher() {
        @Override
        Object get(DataFetchingEnvironment environment) {
            List<String> friendIds = environment.source.friends
            naiveLoadCount += friendIds.size()
            return environment.getDataLoader("character").loadMany(friendIds)
        }
    }

    TypeResolver characterTypeResolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            String id = env.getObject().id
            if (StarWarsData.humanData[id] != null)
                return env.schema.getType("Human") as GraphQLObjectType
            if (StarWarsData.droidData[id] != null)
                return env.schema.getType("Droid") as GraphQLObjectType
            return null
        }
    }


    def starWarsWiring() {
        def episodeValuesProvider = new MapEnumValuesProvider([NEWHOPE: 4, EMPIRE: 5, JEDI: 6])
        def episodeWiring = newTypeWiring("Episode").enumValues(episodeValuesProvider).build()

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("QueryType")
                .dataFetchers(
                [
                        "hero" : heroDataFetcher,
                        "human": humanDataFetcher,
                        "droid": droidDataFetcher
                ])
        )
                .type(newTypeWiring("Human")
                .dataFetcher("friends", friendsDataFetcher)
        )
                .type(newTypeWiring("Droid")
                .dataFetcher("friends", friendsDataFetcher)
        )

                .type(newTypeWiring("Character")
                .typeResolver(characterTypeResolver)
        )
                .type(episodeWiring)
                .build()
        wiring
    }

    def schema = schemaFile("starWarsSchema.graphqls", starWarsWiring())

}
