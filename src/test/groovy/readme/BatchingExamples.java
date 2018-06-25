package readme;

import graphql.GraphQL;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@SuppressWarnings({"unused", "Convert2Lambda", "ConstantConditions", "ClassCanBeStatic"})
public class BatchingExamples {


    class StarWarsCharacter {
        List<String> getFriendIds() {
            return null;
        }
    }

    void starWarsExample() {

        // a batch loader function that will be called with N or more keys for batch loading
        BatchLoader<String, Object> characterBatchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                //
                // we use supplyAsync() of values here for maximum parellisation
                //
                return CompletableFuture.supplyAsync(() -> getCharacterDataViaBatchHTTPApi(keys));
            }
        };

        // a data loader for characters that points to the character batch loader
        DataLoader<String, Object> characterDataLoader = new DataLoader<>(characterBatchLoader);

        //
        // use this data loader in the data fetchers associated with characters and put them into
        // the graphql schema (not shown)
        //
        DataFetcher heroDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                return characterDataLoader.load("2001"); // R2D2
            }
        };

        DataFetcher friendsDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                StarWarsCharacter starWarsCharacter = environment.getSource();
                List<String> friendIds = starWarsCharacter.getFriendIds();
                return characterDataLoader.loadMany(friendIds);
            }
        };

        //
        // DataLoaderRegistry is a place to register all data loaders in that needs to be dispatched together
        // in this case there is 1 but you can have many
        //
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("character", characterDataLoader);

        //
        // this instrumentation implementation will dispatched all the dataloaders
        // as each level fo the graphql query is executed and hence make batched objects
        // available to the query and the associated DataFetchers
        //
        DataLoaderDispatcherInstrumentation dispatcherInstrumentation
                = new DataLoaderDispatcherInstrumentation(registry);

        //
        // now build your graphql object and execute queries on it.
        // the data loader will be invoked via the data fetchers on the
        // schema fields
        //
        GraphQL graphQL = GraphQL.newGraphQL(buildSchema())
                .instrumentation(dispatcherInstrumentation)
                .build();

    }

    private void perRequestGraphQl() {

        GraphQLSchema staticSchema = staticSchema_Or_MayBeFrom_IoC_Injection();

        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("character", getCharacterDataLoader());

        DataLoaderDispatcherInstrumentation dispatcherInstrumentation
                = new DataLoaderDispatcherInstrumentation(registry);

        GraphQL graphQL = GraphQL.newGraphQL(staticSchema)
                .instrumentation(dispatcherInstrumentation)
                .build();

        graphQL.execute("{ helloworld }");

        // you can now throw away the GraphQL and hence DataLoaderDispatcherInstrumentation
        // and DataLoaderRegistry objects since they are really cheap to build per request

    }

    private void doNotUseAsyncInYouDataFetcher() {

        BatchLoader<String, Object> batchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                return CompletableFuture.completedFuture(getTheseCharacters(keys));
            }
        };

        DataLoader<String, Object> characterDataLoader = new DataLoader<>(batchLoader);

        DataFetcher dataFetcherThatCallsTheDataLoader = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                //
                // Don't DO THIS!
                //
                return CompletableFuture.supplyAsync(() -> {
                    String argId = environment.getArgument("id");
                    return characterDataLoader.load(argId);
                });
            }
        };
    }

    private void doAsyncInYourBatchLoader() {

        BatchLoader<String, Object> batchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                return CompletableFuture.supplyAsync(() -> getTheseCharacters(keys));
            }
        };

        DataLoader<String, Object> characterDataLoader = new DataLoader<>(batchLoader);

        DataFetcher dataFetcherThatCallsTheDataLoader = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                //
                // This is OK
                //
                String argId = environment.getArgument("id");
                return characterDataLoader.load(argId);
            }
        };
    }

    private List<Object> getTheseCharacters(List<String> keys) {
        return null;
    }

    private GraphQLSchema staticSchema_Or_MayBeFrom_IoC_Injection() {
        return null;
    }

    private <K, V> DataLoader<K, V> getCharacterDataLoader() {
        return null;
    }

    private GraphQLSchema buildSchema() {
        return null;
    }

    private List<Object> getCharacterDataViaBatchHTTPApi(List<String> keys) {
        return null;
    }


}
