Application concerns
====================

The graphql-java library concentrates on providing an engine for the execution of queries according to the specification.

It does not concern itself about other high level application concerns such as the following :

- Database access
- Caching data
- Data authorisation
- Data pagination
- HTTP transfer
- JSON encoding
- Code wiring via dependency injection

You need to push these concerns into your business logic layers.

The following are great links to read more about this

- http://graphql.org/learn/serving-over-http/
- http://graphql.org/learn/authorization/
- http://graphql.org/learn/pagination/
- http://graphql.org/learn/caching/

Context Objects
^^^^^^^^^^^^^^^

You can pass in a context object during query execution that will allow you to better invoke that business logic.

For example the edge of your application could be performing user detection and you need that information inside the
graphql execution to perform authorisation.

This made up example shows how you can pass yourself information to help execute your queries.

.. code-block:: java

        //
        // this could be code that authorises the user in some way and sets up enough context
        // that can be used later inside data fetchers allowing them
        // to do their job
        //
        UserContext contextForUser = YourGraphqlContextBuilder.getContextForUser(getCurrentUser());

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .context(contextForUser)
                .build();

        ExecutionResult executionResult = graphQL.execute(executionInput);

        // ...
        //
        // later you are able to use this context object when a data fetcher is invoked
        //

        DataFetcher dataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                UserContext userCtx = environment.getContext();
                Long businessObjId = environment.getArgument("businessObjId");

                return invokeBusinessLayerMethod(userCtx, businessObjId);
            }
        };
