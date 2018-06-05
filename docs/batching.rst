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
        // this instrumentation implementation will dispatch all the dataloaders
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


One thing to note is the above only works if you use `DataLoaderDispatcherInstrumentation` which makes sure `dataLoader.dispatch()`
is called.  If this was not in place, then all the promises to data will never be dispatched ot the batch loader function
and hence nothing would ever resolve.

Per Request Data Loaders
^^^^^^^^^^^^^^^^^^^^^^^^

If you are serving web requests then the data can be specific to the user requesting it. If you have user specific data then you will not want to
cache data meant for user A to then later give it to user B in a subsequent request.

The scope of your DataLoader instances is important. You might want to create them per web request to
ensure data is only cached within that web request and no more.

If your data can be shared across web requests then you might want to scope your data loaders so they survive
longer than the web request say.

But if you are doing per request data loaders then creating a new set of ``GraphQL`` and ``DataLoader`` objects per
request is super cheap.  It's the ``GraphQLSchema`` creation that can be expensive, especially if you are using graphql SDL parsing.

Structure your code so that the schema is statically held, perhaps in a static variable or in a singleton IoC component but
build out a new ``GraphQL`` set of objects on each request.


.. code-block:: java

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

Notice above the ``characterDataLoader.load(argId)`` returns immediately.  This will enqueue the call for data until a later time when all
the graphql fields are dispatched.

Then later when the ``DataLoader`` is dispatched, it's ``BatchLoader`` function is called.  This code can be asynchronous so that if you have multiple batch loader
functions they all can run at once.  In the code above ``CompletableFuture.supplyAsync(() -> getTheseCharacters(keys));`` will run the ``getTheseCharacters``
method in another thread.