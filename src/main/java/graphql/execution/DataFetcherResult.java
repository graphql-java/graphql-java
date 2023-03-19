package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.DeprecatedAt;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.PublicApi;
import graphql.schema.DataFetcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;


/**
 * An object that can be returned from a {@link DataFetcher} that contains both data, local context and errors to be added to the final result.
 * This is a useful when your ``DataFetcher`` retrieves data from multiple sources
 * or from another GraphQL resource, or you want to pass extra context to lower levels.
 * <p>
 * This also allows you to pass down new local context objects between parent and child fields.  If you return a
 * {@link #getLocalContext()} value then it will be passed down into any child fields via
 * {@link graphql.schema.DataFetchingEnvironment#getLocalContext()}
 *
 * You can also have {@link DataFetcher}s contribute to the {@link ExecutionResult#getExtensions()} by returning
 * extensions maps that will be merged together via the {@link graphql.extensions.ExtensionsBuilder} and its {@link graphql.extensions.ExtensionsMerger}
 * in place.
 *
 * @param <T> The type of the data fetched
 */
@PublicApi
public class DataFetcherResult<T> {

    private final T data;
    private final List<GraphQLError> errors;
    private final Object localContext;
    private final Map<Object, Object> extensions;

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
    @DeprecatedAt("2019-01-11")
    public DataFetcherResult(T data, List<GraphQLError> errors) {
        this(data, errors, null, null);
    }

    private DataFetcherResult(T data, List<GraphQLError> errors, Object localContext, Map<Object, Object> extensions) {
        this.data = data;
        this.errors = ImmutableList.copyOf(assertNotNull(errors));
        this.localContext = localContext;
        this.extensions = extensions;
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
     * A data fetcher result can supply extension values that will be merged into the result
     * via the {@link graphql.extensions.ExtensionsBuilder} at the end of the operation.
     * <p>
     * The {@link graphql.extensions.ExtensionsMerger} in place inside the {@link graphql.extensions.ExtensionsBuilder}
     * will control how these extension values get merged.
     *
     * @return a map of extension values to be merged
     *
     * @see graphql.extensions.ExtensionsBuilder
     * @see graphql.extensions.ExtensionsMerger
     */
    public Map<Object, Object> getExtensions() {
        return extensions;
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
        private Map<Object, Object> extensions;

        public Builder(DataFetcherResult<T> existing) {
            data = existing.getData();
            localContext = existing.getLocalContext();
            errors.addAll(existing.getErrors());
            extensions = existing.extensions;
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

        public Builder<T> extensions(Map<Object, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public DataFetcherResult<T> build() {
            return new DataFetcherResult<>(data, errors, localContext, extensions);
        }
    }
}
