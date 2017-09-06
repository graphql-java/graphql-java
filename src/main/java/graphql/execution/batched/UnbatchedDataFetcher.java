package graphql.execution.batched;


import graphql.execution.Async;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static graphql.schema.DataFetchingEnvironmentBuilder.newDataFetchingEnvironment;

/**
 * Given a normal data fetcher as a delegate,
 * uses that fetcher in a batched context by iterating through each source value and calling
 * the delegate.
 */
public class UnbatchedDataFetcher implements BatchedDataFetcher {

    private final DataFetcher delegate;

    public UnbatchedDataFetcher(DataFetcher delegate) {
        this.delegate = delegate;
    }


    @Override
    public CompletableFuture<List<Object>> get(DataFetchingEnvironment environment) {
        List<Object> sources = environment.getSource();
        List<CompletableFuture<Object>> results = new ArrayList<>();
        for (Object source : sources) {

            DataFetchingEnvironment singleEnv = newDataFetchingEnvironment(environment)
                    .source(source).build();
            results.add(getResult(delegate.get(singleEnv)));
        }

        return Async.each(results);
    }

    private CompletableFuture<Object> getResult(Object rawResult) {
        if (!(rawResult instanceof CompletionStage)) {
            return CompletableFuture.completedFuture(rawResult);
        }
        return ((CompletionStage) rawResult).toCompletableFuture();
    }
}
