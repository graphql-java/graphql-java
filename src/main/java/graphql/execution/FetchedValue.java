package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Note: This MAY be returned by {@link InstrumentationFieldCompleteParameters#getFetchedObject()}
 * and therefore part of the public despite never used in a method signature.
 */
@PublicApi
@NullMarked
public class FetchedValue {
    private final Object fetchedValue;
    private final Object localContext;
    private final ImmutableList<GraphQLError> errors;

    /**
     * This allows you to get to the underlying fetched value depending on whether the source
     * value is a {@link FetchedValue} or not
     *
     * @param sourceValue the source value in play
     *
     * @return the {@link FetchedValue#getFetchedValue()} if its wrapped otherwise the source value itself
     */
    public static Object getFetchedValue(Object sourceValue) {
        if (sourceValue instanceof FetchedValue) {
            return ((FetchedValue) sourceValue).fetchedValue;
        } else {
            return sourceValue;
        }
    }

    /**
     * This allows you to get to the local context depending on whether the source
     * value is a {@link FetchedValue} or not
     *
     * @param sourceValue the source value in play
     * @param defaultLocalContext the default local context to use
     *
     * @return the {@link FetchedValue#getFetchedValue()} if its wrapped otherwise the default local context
     */
    public static Object getLocalContext(Object sourceValue, Object defaultLocalContext) {
        if (sourceValue instanceof FetchedValue) {
            return ((FetchedValue) sourceValue).localContext;
        } else {
            return defaultLocalContext;
        }
    }

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