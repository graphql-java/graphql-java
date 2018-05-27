Deferred Execution
==================

Often when executing a query you have two classes of data.  The data you need immediately and the data that could arrive little bit later.

For example imagine this query that gets data on a ``post` and its ``comments`` and ``reviews``.


.. code-block:: graphql

        query {
           post {
               postText
               comments {
                   commentText
               }
               reviews {
                   reviewText {
               }
        }

In this form, you *must* wait for the ``comments`` and ``reviews`` data to be retrieved before you can send the ``post`` data back
to the client.  All three data elements are bound to the one query

A naive approach would be to make two queries to gett he most important data first but there is now a better way.

There is ``experimental`` support for deferred execution in graphql-java.

.. code-block:: graphql

        query {
           post {
               postText
               comments @defer {
                   commentText
               }
               reviews @defer {
                   reviewText {
               }
        }

The ``@defer`` directive tells the engine to defer execution of those fields and deliver them later.  The rest of the query is executed as
usual.  There will be the usual  ``ExecutionResult`` of initial data and then a ``org.reactivestreams.Publisher`` of the deferred data.

In the query above, the ``post`` data will be send out in the initial result and then the comments and review data will be sent (in query order)
down a ``Publisher`` later.

You execute your query as you would any other graphql query.  The deferred results ``Publisher`` will be given to you via
the ``extensions`` map


.. code-block:: java

        GraphQLSchema schema = buildSchemaWithDirective();
        GraphQL graphQL = GraphQL.newGraphQL(schema).build();

        //
        // deferredQuery contains the query with @defer directives in it
        //
        ExecutionResult initialResult = graphQL.execute(ExecutionInput.newExecutionInput().query(deferredQuery).build());

        //
        // then initial results happen first, the deferred ones will begin AFTER these initial
        // results have completed
        //
        sendResult(httpServletResponse, initialResult);

        Map<Object, Object> extensions = initialResult.getExtensions();
        Publisher<ExecutionResult> deferredResults = (Publisher<ExecutionResult>) extensions.get(GraphQL.DEFERRED_RESULTS);

        //
        // you subscribe to the deferred results like any other reactive stream
        //
        deferredResults.subscribe(new Subscriber<ExecutionResult>() {

            Subscription subscription;

            @Override
            public void onSubscribe(Subscription s) {
                subscription = s;
                //
                // how many you request is up to you
                subscription.request(10);
            }

            @Override
            public void onNext(ExecutionResult executionResult) {
                //
                // as each deferred result arrives, send it to where it needs to go
                //
                sendResult(httpServletResponse, executionResult);
                subscription.request(10);
            }

            @Override
            public void onError(Throwable t) {
                handleError(httpServletResponse, t);
            }

            @Override
            public void onComplete() {
                completeResponse(httpServletResponse);
            }
        });

The above code subscribes to the deferred results and when each one arrives, sends it down to the client.

You can see more details on reactive-streams code here http://www.reactive-streams.org/

``RxJava`` is a popular implementation of reactive-streams.  Check out http://reactivex.io/intro.html to find out more
about creating Subscriptions.

graphql-java only produces a stream of deferred results.  It does not concern itself with sending these over the network on things
like web sockets and so on.  That is important but not a concern of the base graphql-java library.  Its up to you
to use whatever network mechanism (websockets / long poll / ....) to get results back to you clients.

Also note that this capability is currently ``experimental`` and not defined by the official ``graphql`` specification.  We reserve the
right to change it in a future release or if it enters the official specification.  The graphql-java project
is keen to get feedback on this capability.


