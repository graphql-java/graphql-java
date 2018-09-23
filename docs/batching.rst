Using Dataloader
================

If you are using ``graphql``, you are likely to make queries on a graph of data (surprise surprise).  But it's easy
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


Getting Per Request Data Loaders to Work
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To create a per-request ``DataLoader``, you will certainly be using a dependency injection framework
like Google Guice, or Spring, or Java CDI. These frameworks make sure that an object is created,
put into the "scope" (that is a Map of objects that live as long as the request runs), and 
that it is forgotten when the request has been processed.

One of the fundamental laws of dependency injection is: An object cannot get an injected reference to an
object with a shorter lifetime (i.e. scope) than itself. So, you cannot inject a ``DataLoader``
into a ``DataFetcher`` because the ``DataFetcher`` must already exist at the time the ``RuntimeWiring``
is constructed. That time is long before the first request comes into graphql-java. But: You will
need dependency injection for data loaders because they will need to connect to your backend
services, right?

So, how do you solve this "catch 22"? Let your "query context" object come to rescue.

When you run a ``GraphQL`` query using ``ExecutionInput``, you can specify a "context" object that
will be passed to every ``DataFetcher`` that you use. This query context object usually holds
important information that you need for every query. Example: the currently logged-in user's id.
Now, the solution is to construct the query context object using dependency injection, too,
and add a ``DataLoaderRegistry`` to it that you can use inside your data fetchers. Put all
data loaders that you need into that registry so that you have access to them inside your
``DataFetcher``.

It is a good idea to have a ``QueryContext`` base class with the the context data that
you already have, and add an abstract ``getDataLoader()`` method to it that you implement inside
a ``QueryContextImpl`` class that keeps all the technical bells and whistles. That way, your 
``DataFetcher`` implementations will stay free of additional dependencies to other modules:

.. code-block:: java

        public abstract class QueryContext {

            /**
            * The ID of the user who started this GraphQL query.
            */
            public Long currentUserId;

            /**
            * Get a data loader for a certain purpose.
            */
            public abstract <T> DataLoader<Long, T> getDataLoader(String purpose);
        }

The implementation class might look like this, using dependency injection in the constructor:

.. code-block:: java

        import javax.inject.Inject;
        
        public class QueryContextImpl extends QueryContext {

            private final DataLoaderRegistry dataLoaderRegistry;

            @Inject
            public QueryContextImpl(UserBatchLoader userBatchLoader,
                                    TeamBatchLoader teamBatchLoader) {
                dataLoaderRegistry = new DataLoaderRegistry();
                dataLoaderRegistry.register("users", DataLoader.newMappedDataLoader(userBatchLoader));
                dataLoaderRegistry.register("teams", DataLoader.newMappedDataLoader(teamBatchLoader));
            }

            @Override
            public <T> DataLoader<Long, T> getDataLoader(String purpose) {
                return dataLoaderRegistry.getDataLoader(purpose);
            }

            DataLoaderRegistry getDataLoaderRegistry() {
                return dataLoaderRegistry;
            }
        }

You will use this ``QueryContext`` inside your data fetchers. This example fetches the user to
whom a certain task has been assigned:

.. code-block:: java

        public class TaskAssignedUserFetcher implements DataFetcher<CompletableFuture<User>> {

            @Override
            public CompletableFuture<User> get(DataFetchingEnvironment environment) {
                Task task = environment.getSource();
                QueryContext queryContext = environment.getContext();
                DataLoader<Long, User> userDataLoader = queryContext.getDataLoader("users");
                return task.assignedUserId == null
                        ? CompletableFuture.completedFuture(null)
                        : userDataLoader.load(task.assignedUserId);
            }

        }

The ``DataLoader`` in turn possibly uses a ``MappedBatchLoader`` because your database service
operates with sets of ids instead of lists of ids:

.. code-block:: java

        public class UserBatchLoader implements MappedBatchLoader<Long, User> {

            @Inject
            private UserAppService userAppService;

            @Override
            public CompletionStage<Map<Long, User>> load(Set<Long> objectIdSet) {
                return CompletableFuture.completedFuture(
                        this.userAppService.usersOfIds(objectIdSet)
                                .stream()
                                .collect(Collectors.toMap(object -> object.id, Function.identity()))
                );
            }
        }

OK. Finally, you will connect the data loaders when you really execute a ``GraphQL`` query:

.. code-block:: java

        import javax.inject.Inject;
        import javax.inject.Provider;

        ...some class declaration...
        
        @Inject
        private Provider<QueryContextImpl> queryContextProvider;

        void executeGraphQL() {
            ...
            QueryContextImpl queryContext = queryContextProvider.get();
            queryContext.currentUserId = getTheCurrentUserFromSomewhere();

            ExecutionInput.Builder builder = ExecutionInput.newExecutionInput()
                    .query(myQueryString)
                    .variables(variables)
                    .context(queryContext);

            ExecutionInput executionInput = builder.build();

            DataLoaderDispatcherInstrumentation dispatcherInstrumentation
                    = new DataLoaderDispatcherInstrumentation(queryContext.getDataLoaderRegistry());

            final ExecutionResult executionResult =
                    GraphQL
                            .newGraphQL(this.graphQLSchema)
                            .instrumentation(dispatcherInstrumentation)
                            .build()
                            .execute(executionInput);

Of course, you will tell the dependency injection framework to create a ``QueryContext`` in request
scope. This means that all data loaders will also be created once per request, as planned. Here is
an example for the Ratpack/Guice environment. If you use Spring or Java CDI, this will have to
look differently, of course:

.. code-block:: java

        import com.google.inject.AbstractModule;
        import ratpack.guice.RequestScoped;

        public class GraphQLModule extends AbstractModule {
            @Override
            protected void configure() {
                bind(QueryContextImpl.class).in(RequestScoped.class);
            }
        }

Phew! Quite a few things to care about, right? However, you'll get a lot for the price that you pay.

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