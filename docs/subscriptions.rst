Subscriptions
=============

Subscription Queries
--------------------

Graphql subscriptions allow you subscribe to a reactive source and as new data arrives
then a graphql query is applied over that data and the results are passed on.

See http://graphql.org/blog/subscriptions-in-graphql-and-relay/ for more general details on
graphql subscriptions.


Imagine you have an stock market pricing service and you make a graphql subscription to it like this

.. code-block:: graphql

    subscription StockCodeSubscription {
        stockQuotes(stockCode:"IBM') {
            dateTime
            stockCode
            stockPrice
            stockPriceChange
        }
    }

graphql subscriptions allow a stream of ``ExecutionResult`` objects to be sent down each time the stock price
changes.  The field selection set will applied to the underlying data and are represented just like any other
graphql query.

What is special is that the initial result of a subscription query is a reactive-streams ``Publisher`` object which you
need to use to get the future values.

You need to use ``SubscriptionExecutionStrategy`` as your execution strategy as it has the support for the reactive-streams APIs.

.. code-block:: java

        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy())
                .build();

        ExecutionResult executionResult = graphQL.execute(query);

        Publisher<ExecutionResult> stockPriceStream = executionResult.getData();

The ``Publisher<ExecutionResult>`` here is the publisher of a stream of events.  You need to subscribe to this with your processing
code which will look something like the following


.. code-block:: java

        GraphQL graphQL = GraphQL
                .newGraphQL(schema)
                .subscriptionExecutionStrategy(new SubscriptionExecutionStrategy())
                .build();

        String query = "" +
                "    subscription StockCodeSubscription {\n" +
                "        stockQuotes(stockCode:\"IBM') {\n" +
                "            dateTime\n" +
                "            stockCode\n" +
                "            stockPrice\n" +
                "            stockPriceChange\n" +
                "        }\n" +
                "    }\n";

        ExecutionResult executionResult = graphQL.execute(query);

        Publisher<ExecutionResult> stockPriceStream = executionResult.getData();

        AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();
        stockPriceStream.subscribe(new Subscriber<ExecutionResult>() {

            @Override
            public void onSubscribe(Subscription s) {
                subscriptionRef.set(s);
                s.request(1);
            }

            @Override
            public void onNext(ExecutionResult er) {
                //
                // process the next stock price
                //
                processStockPriceChange(er.getData());

                //
                // ask the publisher for one more item please
                //
                subscriptionRef.get().request(1);
            }

            @Override
            public void onError(Throwable t) {
                //
                // The upstream publishing data source has encountered an error
                // and the subscription is now terminated.  Real production code needs
                // to decide on a error handling strategy.
                //
            }

            @Override
            public void onComplete() {
                //
                // the subscription has completed.  There is not more data
                //
            }
        });

You are now writing reactive-streams code to consume a series of ``ExecutionResults``.  You can see
more details on reactive-streams code here http://www.reactive-streams.org/

``RxJava`` is a popular implementation of reactive-streams.  Check out http://reactivex.io/intro.html to find out more
about creating Publishers of data and Subscriptions to that data.

graphql-java only produces a stream of results.  It does not concern itself with sending these over the network on things
like web sockets and so on.  That is important but not a concern of the base graphql-java library.

We have put together a basic example of using websockets (backed by Jetty) with a simulated stock price application that
is built using RxJava.

See https://github.com/graphql-java/graphql-java-subscription-example for more detailed code on handling network concerns and
the like.


Subscription Data Fetchers
--------------------------

The ``DataFetcher`` behind a subscription field is responsible for creating the ``Publisher`` of data.  The objects
return by this Publisher will be mapped over the graphql query as each arrives and then sent back out as an execution result.

You data fetcher is going to look something like this.


.. code-block:: java

        DataFetcher<Publisher<StockInfo>> publisherDataFetcher = new DataFetcher<Publisher<StockInfo>>() {
            @Override
            public Publisher<StockInfo> get(DataFetchingEnvironment environment) {
                String stockCodeArg = environment.getArgument("stockCode");
                return buildPublisherForStockCode(stockCodeArg);
            }
        };

Now the exact details of how you get that stream of events is up to you and you're reactive code.  graphql-java
gives you a way to map the graphql query fields over that stream of objects just like a standard graphql query.

