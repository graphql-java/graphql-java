package graphql.execution2;

import graphql.GraphQLError;

import java.util.ArrayList;
import java.util.List;

public class FetchedValue {
    private final Object fetchedValue;
    private final Object rawFetchedValue;
    private final List<GraphQLError> errors;

    public FetchedValue(Object fetchedValue, Object rawFetchedValue, List<GraphQLError> errors) {
        this.fetchedValue = fetchedValue;
        this.rawFetchedValue = rawFetchedValue;
        this.errors = errors;
    }

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