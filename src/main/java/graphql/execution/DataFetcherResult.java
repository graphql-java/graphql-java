package graphql.execution;

import graphql.GraphQLError;
import graphql.schema.DataFetcher;

import java.util.List;

import static java.util.Objects.requireNonNull;


/**
 * An object that can be returned from a {@link DataFetcher} that contains both data and errors to be relativized and
 * added to the final result.
 *
 * @param <T> The type of the data fetched
 */
public class DataFetcherResult<T> {

    private final T data;
    private final List<GraphQLError> errors;

    public DataFetcherResult(T data, List<GraphQLError> errors) {
        this.data = data;
        this.errors = requireNonNull(errors);
    }

    /**
     * @return The data fetched. May be null
     */
    public T getData() {
        return data;
    }

    /**
     * @return errors encountered when fetching data.  Must not be null.
     */
    public List<GraphQLError> getErrors() {
        return errors;
    }
}
