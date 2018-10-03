Using Dataloader
================

If you are using ``graphql``, you are likely to making queries on a graph of data (surprise surprise).  But it's easy
to implement inefficient code with naive loading of a graph of data.

Using ``java-dataloader`` will help you to make this a more efficient process by both caching and batching requests for that graph of data items.  If ``dataloader``
has seen a data item before, it will have cached the value and will return it without having to ask for it again.

Imagine we have the StarWars query outlined below.  It asks us to find a hero and their friend's names and their friend's friend's
names.  It is likely that many of these people will be friends in common.


.. code-block:: graphql

        {
            hero {
                name
                friends {
                    name
                    friends {
                       name
                    }
                }
            }
        }

The result of this query is displayed below. You can see that Han, Leia, Luke and R2-D2 are a tight knit bunch of friends and
share many friends in common.

.. code-block:: json

        [
          hero: [
            name: 'R2-D2',
            friends: [
              [
                name: 'Luke Skywalker',
                friends: [
                  [name: 'Han Solo'],
                  [name: 'Leia Organa'],
                  [name: 'C-3PO'],
                  [name: 'R2-D2']
                ]
              ],
              [
                name: 'Han Solo',
                friends: [
                  [name: 'Luke Skywalker'],
                  [name: 'Leia Organa'],
                  [name: 'R2-D2']
                ]
              ],
              [
                name: 'Leia Organa',
                friends: [
                  [name: 'Luke Skywalker'],
                  [name: 'Han Solo'],
                  [name: 'C-3PO'],
                  [name: 'R2-D2']
                ]
              ]
            ]
          ]
        ]

A naive implementation would call a `DataFetcher` to retrieve a person object every time it was invoked.

In this case it would be *15* calls over the network.  Even though the group of people have a lot of common friends.
With `dataloader` you can make the `graphql` query much more efficient.

As `graphql` descends each level of the query (e.g. as it processes `hero` and then `friends` and then for each their `friends`),
the data loader is called to "promise" to deliver a person object.  At each level `dataloader.dispatch()` will be
called to fire off the batch requests for that part of the query. With caching turned on (the default) then
any previously returned person will be returned as-is for no cost.

In the above example there are only *5* unique people mentioned but with caching and batching retrieval in place there will be only
*3* calls to the batch loader function.  *3* calls over the network or to a database is much better than *15* calls, you will agree.

If you use capabilities like `java.util.concurrent.CompletableFuture.supplyAsync()` then you can make it even more efficient by making the
the remote calls asynchronous to the rest of the query.  This will make it even more timely since multiple calls can happen at once
if need be.

Here is how you might put this in place:


.. code-block:: java

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
        DataLoaderDispatcherInstrumentationOptions options = DataLoaderDispatcherInstrumentationOptions
                .newOptions().includeStatistics(true);

        DataLoaderDispatcherInstrumentation dispatcherInstrumentation
                = new DataLoaderDispatcherInstrumentation(options);

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
        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(characterBatchLoader);

        //
        // DataLoaderRegistry is a place to register all data loaders in that needs to be dispatched together
        // in this case there is 1 but you can have many.
        //
        // Also note that the data loaders are created per execution request
        //
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("character", characterDataLoader);

        ExecutionInput executionInput = newExecutionInput()
                .query(getQuery())
                .dataLoaderRegistry(registry)
                .build();

        ExecutionResult executionResult = graphQL.execute(executionInput);

In this example we explicitly added the `DataLoaderDispatcherInstrumentation` because we wanted to tweak its options.  However
it will be automatically added for you if you don't add it manually.

Data Loader only works with AsyncExecutionStrategy
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The only execution that works with DataLoader is ``graphql.execution.AsyncExecutionStrategy``.  This is because this execution strategy knows
then the most optimal time to dispatch() your load calls is.  It does this by deeply tracking how many fields are outstanding and whether they
are list values and so on.

Other execution strategies such as ``ExecutorServiceExecutionStrategy`` cant do this and hence if the data loader code detects
you are not using ``AsyncExecutionStrategy`` then it will simple dispatch the data loader as each field is encountered.  You
may get `caching` of values but you will not get `batching` of them.


Per Request Data Loaders
^^^^^^^^^^^^^^^^^^^^^^^^

If you are serving web requests then the data can be specific to the user requesting it. If you have user specific data then you will not want to
cache data meant for user A to then later give it to user B in a subsequent request.

The scope of your DataLoader instances is important. You will want to create them per web request to
ensure data is only cached within that web request and no more. It also ensures that a ``dispatch`` call
only affects that graphql execution and no other.

DataLoaders by default act as caches.  If they have seen a value before for a key then they will automatically return
it in order to be efficient.

If your data can be shared across web requests then you might want to change the caching implementation of your data loaders so they share
data via a caching layer say like memcached or redis.

You still create data loaders per request, however the caching layer will allow data sharing (if that's suitable).


.. code-block:: java

        CacheMap<String, Object> crossRequestCacheMap = new CacheMap<String, Object>() {
            @Override
            public boolean containsKey(String key) {
                return redisIntegration.containsKey(key);
            }

            @Override
            public Object get(String key) {
                return redisIntegration.getValue(key);
            }

            @Override
            public CacheMap<String, Object> set(String key, Object value) {
                redisIntegration.setValue(key, value);
                return this;
            }

            @Override
            public CacheMap<String, Object> delete(String key) {
                redisIntegration.clearKey(key);
                return this;
            }

            @Override
            public CacheMap<String, Object> clear() {
                redisIntegration.clearAll();
                return this;
            }
        };

        DataLoaderOptions options = DataLoaderOptions.newOptions().setCacheMap(crossRequestCacheMap);

        DataLoader<String, Object> dataLoader = DataLoader.newDataLoader(batchLoader, options);


Async Calls On Your Batch Loader Function Only
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The data loader code pattern works by combining all the outstanding data loader calls into more efficient batch loading calls.

graphql-java tracks what outstanding data loader calls have been made and it is its responsibility to call ``dispatch``
in the background at the most optimal time, which is when all graphql fields have been examined and dispatched.

However there is a code pattern that will cause your data loader calls to never complete and these *MUST* be avoided.  This bad
pattern consists of making a an asynchronous off thread call to a ``DataLoader`` in your data fetcher.

The following will not work (it will never complete).

.. code-block:: java

        BatchLoader<String, Object> batchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                return CompletableFuture.completedFuture(getTheseCharacters(keys));
            }
        };

        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(batchLoader);

        // .... later in your data fetcher

        DataFetcher dataFetcherThatCallsTheDataLoader = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                //
                // Don't DO THIS!
                //
                return CompletableFuture.supplyAsync(() -> {
                    String argId = environment.getArgument("id");
                    DataLoader<String, Object> characterLoader = environment.getDataLoader("characterLoader");
                    return characterLoader.load(argId);
                });
            }
        };

In the example above, the call to ``characterDataLoader.load(argId)`` can happen some time in the future on another thread.  The graphql-java
engine has no way of knowing when it's good time to dispatch outstanding ``DataLoader`` calls and hence the data loader call might never complete
as expected and no results will be returned.

Remember a data loader call is just a promise to actually get a value later when its an optimal time for all outstanding calls to be batched
together.  The most optimal time is when the graphql field tree has been examined and all field values are currently dispatched.

The following is how you can still have asynchronous code, by placing it into the ``BatchLoader`` itself.

.. code-block:: java

        BatchLoader<String, Object> batchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                return CompletableFuture.supplyAsync(() -> getTheseCharacters(keys));
            }
        };

        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(batchLoader);

        // .... later in your data fetcher

        DataFetcher dataFetcherThatCallsTheDataLoader = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                //
                // This is OK
                //
                String argId = environment.getArgument("id");
                DataLoader<String, Object> characterLoader = environment.getDataLoader("characterLoader");
                return characterLoader.load(argId);
            }
        };

Notice above the ``characterDataLoader.load(argId)`` returns immediately.  This will enqueue the call for data until a later time when all
the graphql fields are dispatched.

Then later when the ``DataLoader`` is dispatched, it's ``BatchLoader`` function is called.  This code can be asynchronous so that if you have multiple batch loader
functions they all can run at once.  In the code above ``CompletableFuture.supplyAsync(() -> getTheseCharacters(keys));`` will run the ``getTheseCharacters``
method in another thread.

Passing context to your data loader
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The data loader library supports two types of context being passed to the batch loader. The first is
an overall context object per dataloader and the second is a map of per loaded key context objects.

This allows you to pass in the extra details you may need to make downstream calls.  The dataloader key is used
in the caching of results but the context objects can be made available to help with the call.

So in the example below we have an overall security context object that gives out a call token and we also pass the graphql source
object to each ``dataLoader.load()`` call.

.. code-block:: java

        BatchLoaderWithContext<String, Object> batchLoaderWithCtx = new BatchLoaderWithContext<String, Object>() {

            @Override
            public CompletionStage<List<Object>> load(List<String> keys, BatchLoaderEnvironment loaderContext) {
                //
                // we can have an overall context object
                SecurityContext securityCtx = loaderContext.getContext();
                //
                // and we can have a per key set of context objects
                Map<Object, Object> keysToSourceObjects = loaderContext.getKeyContexts();

                return CompletableFuture.supplyAsync(() -> getTheseCharacters(securityCtx.getToken(), keys, keysToSourceObjects));
            }
        };

        // ....

        SecurityContext securityCtx = SecurityContext.newSecurityContext();

        BatchLoaderContextProvider contextProvider = new BatchLoaderContextProvider() {
            @Override
            public Object getContext() {
                return securityCtx;
            }
        };
        //
        // this creates an overall context for the dataloader
        //
        DataLoaderOptions loaderOptions = DataLoaderOptions.newOptions().setBatchLoaderContextProvider(contextProvider);
        DataLoader<String, Object> characterDataLoader = DataLoader.newDataLoader(batchLoaderWithCtx, loaderOptions);

        // .... later in your data fetcher

        DataFetcher dataFetcherThatCallsTheDataLoader = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                String argId = environment.getArgument("id");
                Object source = environment.getSource();
                //
                // you can pass per load call contexts
                //
                return characterDataLoader.load(argId, source);
            }
        };

