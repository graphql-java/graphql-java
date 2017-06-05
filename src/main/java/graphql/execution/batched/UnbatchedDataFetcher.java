package graphql.execution.batched;


import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

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
        this.delegate = delegate;
    }


    @Override
    public Object get(DataFetchingEnvironment environment) {
        List<Object> sources = environment.getSource();
        List<Object> results = new ArrayList<>();
        for (Object source : sources) {
            DataFetchingEnvironment singleEnv = new DataFetchingEnvironmentImpl(
                    source,
                    environment.getArguments(),
                    environment.getContext(),
                    environment.getRoot(),
                    environment.getFields(),
                    environment.getFieldType(),
                    environment.getParentType(),
                    environment.getGraphQLSchema(),
                    environment.getFragmentsByName(),
                    environment.getExecutionId(),
                    environment.getSelectionSet());
            results.add(delegate.get(singleEnv));
        }
        return results;
    }
}
