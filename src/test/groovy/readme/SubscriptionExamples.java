package readme;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.SubscriptionExecutionStrategy;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicReference;

import static graphql.MutationSchema.schema;

@SuppressWarnings("Convert2Lambda")
public class SubscriptionExamples {

    void basicSubscriptionExample() {
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
    }

    static void dataFetcherExample() {
        DataFetcher<Publisher<StockInfo>> publisherDataFetcher = new DataFetcher<Publisher<StockInfo>>() {
            @Override
            public Publisher<StockInfo> get(DataFetchingEnvironment environment) {
                String stockCodeArg = environment.getArgument("stockCode");
                return buildPublisherForStockCode(stockCodeArg);
            }
        };
    }

    static class StockInfo {
    }

    private static Publisher<StockInfo> buildPublisherForStockCode(String stockCodeArg) {
        return null;
    }

    private void processStockPriceChange(Object data) {
    }

}
