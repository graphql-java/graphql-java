package graphql.execution.batched;


import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a normal data fetcher as a delegate,
 * uses that fetcher in a batched context by iterating through each source value and calling
 * the delegate.
 */
public class UnbatchedDataFetcher implements BatchedDataFetcher {

    private final DataFetcher delegate;

    public UnbatchedDataFetcher(DataFetcher delegate) {
        assert !(delegate instanceof BatchedDataFetcher);
        this.delegate = delegate;
    }


    @Override
    public Object get(DataFetchingEnvironment environment) {
        @SuppressWarnings("unchecked")
        List<Object> sources = (List<Object>) environment.getSource();
        List<Object> results = new ArrayList<Object>();
        for (Object source : sources) {
            DataFetchingEnvironment singleEnv = new DataFetchingEnvironment(
                    source,
                    environment.getArguments(),
                    environment.getContext(),
                    environment.getFields(),
                    environment.getFieldType(),
                    environment.getParentType(),
                    environment.getGraphQLSchema());
            results.add(delegate.get(singleEnv));
        }
        return results;
    }
}
