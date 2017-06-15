package graphql.execution.batched;


import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;

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
    public Object get(DataFetchingEnvironment environment) {
        List<Object> sources = environment.getSource();
        List<Object> results = new ArrayList<>();
        for (Object source : sources) {

            DataFetchingEnvironment singleEnv = newDataFetchingEnvironment(environment)
                    .source(source).build();
            results.add(delegate.get(singleEnv));
        }
        return results;
    }
}
