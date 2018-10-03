package readme;

import graphql.ExecutionInput;
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

    private String getQuery() {
        return null;
    }

    void starWarsExample() {

        //
        // a batch loader function that will be called with N or more keys for batch loading
        // This can be a singleton object since its stateless
        //
        BatchLoader<String, Object> characterBatchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                //
                // we use supplyAsync() of values here for maximum parellisation
                //
                return CompletableFuture.supplyAsync(() -> getCharacterDataViaBatchHTTPApi(keys));
            }
        };


        //
        // use this data loader in the data fetchers associated with characters and put them into
        // the graphql schema (not shown)
        //
        DataFetcher heroDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                DataLoader<String, Object> dataLoader = environment.getDataLoader("character");
                return dataLoader.load("2001"); // R2D2
            }
        };

        DataFetcher friendsDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                StarWarsCharacter starWarsCharacter = environment.getSource();
                List<String> friendIds = starWarsCharacter.getFriendIds();
                DataLoader<String, Object> dataLoader = environment.getDataLoader("character");
                return dataLoader.loadMany(friendIds);
            }
        };


        //
        // this instrumentation implementation will dispatched all the data loaders
        // as each level fo the graphql query is executed and hence make batched objects
        // available to the query and the associated DataFetchers
        //
        DataLoaderDispatcherInstrumentation dispatcherInstrumentation
                = new DataLoaderDispatcherInstrumentation();

        //
        // now build your graphql object and execute queries on it.
        // the data loader will be invoked via the data fetchers on the
        // schema fields
        //
        GraphQL graphQL = GraphQL.newGraphQL(buildSchema())
                .instrumentation(dispatcherInstrumentation)
                .build();

        //
        // a data loader for characters that points to the character batch loader
        //
        // Since data loaders are stateful, they are created per execution request.
        //
        DataLoader<String, Object> characterDataLoader = new DataLoader<>(characterBatchLoader);

        //
        // DataLoaderRegistry is a place to register all data loaders in that needs to be dispatched together
        // in this case there is 1 but you can have many
        //
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("character", characterDataLoader);

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query(getQuery())
                .dataLoaderRegistry(registry)
                .build();

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
