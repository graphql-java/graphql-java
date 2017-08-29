Execution
=========

Queries
-------

To execute a query against a schema build a new ``GraphQL`` object with the appropriate arguments and then
call ``execute()``.

The result of a query is an ``ExecutionResult`` which is the query data and/or a list of errors.

.. code-block:: java

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        GraphQL graphQL = GraphQL.newGraphQL(schema)
                .build();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("query { hero { name } }")
                .build();

        ExecutionResult executionResult = graphQL.execute(executionInput);

        Object data = executionResult.getData();
        List<GraphQLError> errors = executionResult.getErrors();


More complex query examples can be found in the `StarWars query tests <https://github.com/graphql-java/graphql-java/blob/master/src/test/groovy/graphql/StarWarsQueryTest.groovy>`_

Each graphql field type has a ``graphql.schema.DataFetcher`` associated with it.  Often you
can rely on ``graphql.schema.PropertyDataFetcher`` to examine Java POJO objects to
provide field values from them.

However you will need to wire in your top level domain objects via your own custom data fetchers.  This might involve making
a database call or contacting another system over HTTP say.

graphql-java is not opinionated about how you get your domain data objects, that is very much your concern.  It is also not
opinionated on user authorisation to that data.  You should push all that logic into your business logic layer code.

A data fetcher might look like this:

.. code-block:: java

        DataFetcher userDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                return fetchUserFromDatabase(environment.getArgument("userId"));
            }
        };

Each ``DataFetcher`` is passed a ``graphql.schema.DataFetchingEnvironment`` object which contains what field is being fetched, what
arguments have been supplied to the field and other information such as the field's parent object, the query root object or the query
context object.

In the above example, the execution will wait for the data fetcher to return before moving on.  You can make execution of
the ``DataFetcher`` asynchronous by returning a ``CompletionStage`` to data, that is explained more further down this page.


Mutations
---------

A good starting point to learn more about mutating data in graphql is `http://graphql.org/learn/queries/#mutations <http://graphql.org/learn/queries/#mutations>`_.

In essence you need to define a ``GraphQLObjectType`` that takes arguments as input.  Those arguments are what you can use to mutate your data store
via the data fetcher invoked.

The mutation is invoked via a query like :

.. code-block:: graphql

    mutation CreateReviewForEpisode($ep: Episode!, $review: ReviewInput!) {
      createReview(episode: $ep, review: $review) {
        stars
        commentary
      }
    }

You need to send in arguments during that mutation operation, in this case for the variables for ``$ep`` and ``$review``

You would create types like this to handle this mutation :

.. code-block:: java

    GraphQLInputObjectType episodeType = GraphQLInputObjectType.newInputObject()
            .name("Episode")
            .field(newInputObjectField()
                    .name("episodeNumber")
                    .type(Scalars.GraphQLInt))
            .build();

    GraphQLInputObjectType reviewInputType = GraphQLInputObjectType.newInputObject()
            .name("ReviewInput")
            .field(newInputObjectField()
                    .name("stars")
                    .type(Scalars.GraphQLString))
            .field(newInputObjectField()
                    .name("commentary")
                    .type(Scalars.GraphQLString))
            .build();   

    GraphQLObjectType reviewType = newObject()
            .name("Review")
            .field(newFieldDefinition()
                    .name("stars")
                    .type(GraphQLString))
            .field(newFieldDefinition()
                    .name("commentary")
                    .type(GraphQLString))
            .build();

    GraphQLObjectType createReviewForEpisodeMutation = newObject()
            .name("CreateReviewForEpisodeMutation")
            .field(newFieldDefinition()
                    .name("createReview")
                    .type(reviewType)
                    .argument(newArgument()
                            .name("episode")
                            .type(episodeType)
                    )
                    .argument(newArgument()
                            .name("review")
                            .type(reviewInputType)
                    )
                    .dataFetcher(mutationDataFetcher())
            )
            .build();

    GraphQLSchema schema = GraphQLSchema.newSchema()
            .query(queryType)
            .mutation(createReviewForEpisodeMutation)
            .build();


Notice that the input arguments are of type ``GraphQLInputObjectType``.  This is important.  Input arguments can ONLY be of that type
and you cannot use output types such as ``GraphQLObjectType``.  Scalars types are consider both input and output types.

The data fetcher here is responsible for executing the mutation and returning some sensible output values.

.. code-block:: java

    private DataFetcher mutationDataFetcher() {
        return new DataFetcher() {
            @Override
            public Review get(DataFetchingEnvironment environment) {
                Episode episode = environment.getArgument("episode");
                ReviewInput review = environment.getArgument("review");

                // make a call to your store to mutate your database
                Review updatedReview = reviewStore().update(episode, review);

                // this returns a new view of the data
                return updatedReview;
            }
        };
    }

Notice how it calls a data store to mutate the backing database and then returns a ``Review`` object that can be used as the output values
to the caller.

Asynchronous Execution
----------------------

graphql-java uses fully asynchronous execution techniques when it executes queries.  You can get the ``CompleteableFuture`` to results by calling
``executeAsync()`` like this

.. code-block:: java

        GraphQL graphQL = buildSchema();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput().query("query { hero { name } }")
                .build();

        CompletableFuture<ExecutionResult> promise = graphQL.executeAsync(executionInput);

        promise.thenAccept(executionResult -> {
            // here you might send back the results as JSON over HTTP
            encodeResultToJsonAndSendResponse(executionResult);
        });

        promise.join();

The use of ``CompletableFuture`` allows you to compose actions and functions that will be applied when the execution completes.  The final
call to ``.join()`` waits for the execution to happen.

In fact under the covers, the graphql-java engine uses asynchronous execution and makes the ``.execute()`` method appear synchronous by
calling join for you.  So the following code is in fact the same.

.. code-block:: java

        ExecutionResult executionResult = graphQL.execute(executionInput);

        // the above is equivalent to the following code (in long hand)

        CompletableFuture<ExecutionResult> promise = graphQL.executeAsync(executionInput);
        ExecutionResult executionResult2 = promise.join();



If a ``graphql.schema.DataFetcher`` returns a ``CompletableFuture<T>`` object then this will be composed into the overall asynchronous
query execution.  This means you can fire off a number of field fetching requests in parallel.  Exactly what
threading strategy you use is up to your data fetcher code.

The following code uses the standard Java ``java.util.concurrent.ForkJoinPool.commonPool()`` thread executor to supply values in another
thread.

.. code-block:: java

        DataFetcher userDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                CompletableFuture<User> userPromise = CompletableFuture.supplyAsync(() -> {
                    return fetchUserViaHttp(environment.getArgument("userId"));
                });
                return userPromise;
            }
        };

The code above is written in long form.  With Java 8 lambdas it can be written more succinctly as follows

.. code-block:: java

        DataFetcher userDataFetcher = environment -> CompletableFuture.supplyAsync(
                () -> fetchUserViaHttp(environment.getArgument("userId")));

The graphql-java engine ensures that all the ``CompleteableFuture`` objects are composed together to provide an execution result
that follows the graphql specification.

Execution Strategies
--------------------

A class derived from ``graphql.execution.ExecutionStrategy`` is used to run a query or mutation.  A number of different
strategies are provided with graphql-java and if you are really keen you can even write your own.

You can wire in what execution strategy to use when you create the ``GraphQL`` object.


.. code-block:: java

        GraphQL.newGraphQL(schema)
                .queryExecutionStrategy(new AsyncExecutionStrategy())
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy())
                .build();

In fact the code above is equivalent to the default settings and is a very sensible choice of execution
strategies for most cases.

AsyncExecutionStrategy
^^^^^^^^^^^^^^^^^^^^^^

By default the "query" execution strategy is ``graphql.execution.AsyncExecutionStrategy`` which will dispatch
each field as ``CompleteableFuture`` objects and not care which ones complete first.  This strategy allows for the most
performant execution.

The data fetchers invoked can themselves return `CompletionStage`` values and this will create
fully asynchronous behaviour.

So imagine a query as follows

.. code-block:: graphql

    query {
      hero {
        enemies {
          name
        }
        friends {
          name
        }
      }
    }


The ``AsyncExecutionStrategy`` is free to dispatch the *enemies* field at the same time as the *friends* field.  It does not
have to do *enemies* first followed by *friends*, which would be less efficient.

It will however assemble the results in order.  The query result will follow the graphql specification and return object values
assembled in query field order.  Only the execution of data fetching is free to be in any order.

This behaviour is allowed in the graphql specification and in fact is actively encouraged http://facebook.github.io/graphql/#sec-Query
for read only queries.

See `specification <http://facebook.github.io/graphql/#sec-Normal-evaluation>`_ for details.


AsyncSerialExecutionStrategy
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The graphql specification says that mutations MUST be executed serially and in the order in which the
query fields occur.

So ``graphql.execution.AsyncSerialExecutionStrategy`` is used by default for mutations and will ensure that each
field is completed before it processes the next one and so forth.  You can still return ``CompletionStage`` objects
in the mutation data fetchers, however they will be executed serially and will be completed before the next
mutation field data fetcher is dispatched.

ExecutorServiceExecutionStrategy
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The ``graphql.execution.ExecutorServiceExecutionStrategy`` execution strategy will always dispatch each field
fetch in an asynchronous manner, using the executor you give it.  It differs from ``AsyncExecutionStrategy`` in that
it does not rely on the data fetchers to be asynchronous but rather makes the field fetch invocation asynchronous by
submitting each field to the provided `java.util.concurrent.ExecutorService`.

This behaviour makes it unsuitable to be used as a mutation execution strategy.

.. code-block:: java

        ExecutorService  executorService = new ThreadPoolExecutor(
                2, /* core pool size 2 thread */
                2, /* max pool size 2 thread */
                30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new ThreadPoolExecutor.CallerRunsPolicy());

        GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .queryExecutionStrategy(new ExecutorServiceExecutionStrategy(executorService))
                .mutationExecutionStrategy(new AsyncSerialExecutionStrategy())
                .build();



BatchedExecutionStrategy
^^^^^^^^^^^^^^^^^^^^^^^^

Alternatively, schemas with nested lists may benefit from using a ``graphql.execution.batched.BatchedExecutionStrategy`` and
creating batched DataFetchers with get() methods annotated @Batched.


.. This text will not be shown and if it does I have not done restructured comments right.  We should add more details
   on how BatchedExecutionStrategy works here.  Its a pretty special case that I don't know how to explain properly


Query Caching
-------------

Before the ``graphql-java`` engine executes a query it must be parsed and validated, and this process can be somewhat time consuming.

To avoid the need for re-parse/validate the ``GraphQL.Builder`` allows an instance of ``PreparsedDocumentProvider`` to reuse ``Document`` instances.

Please note that this does not cache the result of the query, only the parsed ``Document``.

.. code-block:: java

    Cache<String, PreparsedDocumentEntry> cache = Caffeine.newBuilder().maximumSize(10_000).build(); (1)
    GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
            .preparsedDocumentProvider(cache::get) (2)
            .build();


1. Create an instance of preferred cache instance, here is `Caffeine <https://github.com/ben-manes/caffeine>`_  used as it is a high quality caching solution. The cache instance should be thread safe and shared.
2. The ``PreparsedDocumentProvider`` is a functional interface with only a get method and we can therefore pass a method reference that matches the signature into the builder.


In order to achieve high cache hit ration it is recommended that field arguments are passed in as variables instead of directly in the query.

The following query:

.. code-block:: json

    query HelloTo {
         sayHello(to: "Me") {
            greeting
         }
    }

Should be rewritten as:

.. code-block:: json

    query HelloTo($to: String!) {
         sayHello(to: $to) {
            greeting
         }
    }

with variables:

.. code-block:: json

    {
       "to": "Me"
    }

The query is now reused regardless of variable values provided.
