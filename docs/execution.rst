Execution
============


To execute a Query/Mutation against a Schema build a new ``GraphQL`` Object with the appropriate arguments and then call ``execute()``.

The result of a Query is a ``ExecutionResult`` Object with the result and/or a list of Errors.

Example: [GraphQL Test](src/test/groovy/graphql/GraphQLTest.groovy)

More complex examples: [StarWars query tests](src/test/groovy/graphql/StarWarsQueryTest.groovy)


Mutations
----------

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

Execution strategies
--------------------

All fields in a SelectionSet are executed serially per default.

You can however provide your own execution strategies, one to use while querying data and one
to use when mutating data.

.. code-block:: java

    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            2, /* core pool size 2 thread */
            2, /* max pool size 2 thread */
            30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadPoolExecutor.CallerRunsPolicy());

    GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
            .queryExecutionStrategy(new ExecutorServiceExecutionStrategy(threadPoolExecutor))
            .mutationExecutionStrategy(new SimpleExecutionStrategy())
            .build();



When provided fields will be executed parallel, except the first level of a mutation operation.

See `specification <http://facebook.github.io/graphql/#sec-Normal-evaluation>`_ for details.

Alternatively, schemas with nested lists may benefit from using a BatchedExecutionStrategy and creating batched DataFetchers with get() methods annotated @Batched.


Query caching
-------------

Before ``graphql-java`` engine executes a query it must be parsed and validated, and this process can be somewhat time consuming.
To avoid the need for re-parse/validate the ``GraphQL.Builder`` allows an instance of ``PreparsedDocumentProvider`` to reuse ``Document`` instances.

Please note that this does not cache the result of the query, only the parsed ``Document``.

.. code-block:: java

    Cache<String, PreparsedDocumentEntry> cache = Caffeine.newBuilder().maximumSize(10_000).build(); (1)
    GraphQL graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
            .preparsedDocumentProvider(cache::get) (2)
            .build();

1. Create an instance of preferred cache instance, here is `Caffeine <https://github.com/ben-manes/caffeine>`_  used as it is a high quality caching solution. The cache instance should be thread safe and shared.
2. The ``PreparsedDocumentProvider`` is a functional interface with only a get method and we can therefore pass a method reference that matches the signature
into the builder.


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
