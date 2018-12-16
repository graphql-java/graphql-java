package graphql.execution2;

import graphql.GraphQLError;
import graphql.Internal;

import java.util.ArrayList;
import java.util.List;

@Internal
public class FetchedValue {
    private final Object fetchedValue;
    private final Object rawFetchedValue;
    private final List<GraphQLError> errors;

    public FetchedValue(Object fetchedValue, Object rawFetchedValue, List<GraphQLError> errors) {
        this.fetchedValue = fetchedValue;
        this.rawFetchedValue = rawFetchedValue;
        this.errors = errors;
    }

    /*
     * the unboxed value meaning not Optional, not DataFetcherResult etc
     */
    public Object getFetchedValue() {
        return fetchedValue;
    }

    public Object getRawFetchedValue() {
        return rawFetchedValue;
    }

    public List<GraphQLError> getErrors() {
        return new ArrayList<>(errors);
    }
}