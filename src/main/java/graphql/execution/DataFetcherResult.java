package graphql.execution;

import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.schema.DataFetcher;

import java.util.List;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.unmodifiableList;


/**
 * An object that can be returned from a {@link DataFetcher} that contains both data and errors to be relativized and
 * added to the final result. This is a useful when your ``DataFetcher`` retrieves data from multiple sources
 * or from another GraphQL resource.
 *
 * @param <T> The type of the data fetched
 */
@PublicApi
public class DataFetcherResult<T> {

    private final T data;
    private final List<GraphQLError> errors;

    public DataFetcherResult(T data, List<GraphQLError> errors) {
        this.data = data;
        this.errors = unmodifiableList(assertNotNull(errors));
    }

    /**
     * @return The data fetched. May be null.
     */
    public T getData() {
        return data;
    }

    /**
     * @return errors encountered when fetching data.  This will be non null but possibly empty.
     */
    public List<GraphQLError> getErrors() {
        return errors;
    }
}
