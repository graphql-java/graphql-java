package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.PublicApi;
import graphql.schema.DataFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;


/**
 * An object that can be returned from a {@link DataFetcher} that contains both data, local context and errors to be relativized and
 * added to the final result. This is a useful when your ``DataFetcher`` retrieves data from multiple sources
 * or from another GraphQL resource or you want to pass extra context to lower levels.
 *
 * This also allows you to pass down new local context objects between parent and child fields.  If you return a
 * {@link #getLocalContext()} value then it will be passed down into any child fields via
 * {@link graphql.schema.DataFetchingEnvironment#getLocalContext()}
 *
 * @param <T> The type of the data fetched
 */
@PublicApi
public class DataFetcherResult<T> {

    private final T data;
    private final List<GraphQLError> errors;
    private final Object localContext;

    /**
     * Creates a data fetcher result
     *
     * @param data   the data
     * @param errors the errors
     *
     * @deprecated use the {@link #newResult()} builder instead
     */
    @Internal
    @Deprecated
    public DataFetcherResult(T data, List<GraphQLError> errors) {
        this(data, errors, null);
    }

    private DataFetcherResult(T data, List<GraphQLError> errors, Object localContext) {
        this.data = data;
        this.errors = ImmutableList.copyOf(assertNotNull(errors));
        this.localContext = localContext;
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

    /**
     * @return true if there are any errors present
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * A data fetcher result can supply a context object for that field that is passed down to child fields
     *
     * @return a local context object
     */
    public Object getLocalContext() {
        return localContext;
    }

    /**
     * This helps you transform the current DataFetcherResult into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new instance produced by calling {@code build} on that builder
     */
    public DataFetcherResult<T> transform(Consumer<Builder<T>> builderConsumer) {
        Builder<T> builder = new Builder<>(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    /**
     * Creates a new data fetcher result builder
     *
     * @param <T> the type of the result
     *
     * @return a new builder
     */
    public static <T> Builder<T> newResult() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private T data;
        private Object localContext;
        private final List<GraphQLError> errors = new ArrayList<>();

        public Builder(DataFetcherResult<T> existing) {
            data = existing.getData();
            localContext = existing.getLocalContext();
            errors.addAll(existing.getErrors());
        }

        public Builder(T data) {
            this.data = data;
        }

        public Builder() {
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> errors(List<GraphQLError> errors) {
            this.errors.addAll(errors);
            return this;
        }

        public Builder<T> error(GraphQLError error) {
            this.errors.add(error);
            return this;
        }

        public Builder<T> clearErrors() {
            this.errors.clear();
            return this;
        }

        /**
         * @return true if there are any errors present
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public Builder<T> localContext(Object localContext) {
            this.localContext = localContext;
            return this;
        }

        public DataFetcherResult<T> build() {
            return new DataFetcherResult<>(data, errors, localContext);
        }
    }
}
