package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;

import java.util.List;

/**
 * Note: This is returned by {@link InstrumentationFieldCompleteParameters#getFetchedValue()}
 * and therefore part of the public despite never used in a method signature.
 */
@PublicApi
public class FetchedValue {
    private final Object fetchedValue;
    private final Object localContext;
    private final ImmutableList<GraphQLError> errors;

    public FetchedValue(Object fetchedValue, List<GraphQLError> errors, Object localContext) {
        this.fetchedValue = fetchedValue;
        this.errors = ImmutableList.copyOf(errors);
        this.localContext = localContext;
    }

    /*
     * the unboxed value meaning not Optional, not DataFetcherResult etc
     */
    public Object getFetchedValue() {
        return fetchedValue;
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    public Object getLocalContext() {
        return localContext;
    }

    @Override
    public String toString() {
        return "FetchedValue{" +
                "fetchedValue=" + fetchedValue +
                ", localContext=" + localContext +
                ", errors=" + errors +
                '}';
    }

}