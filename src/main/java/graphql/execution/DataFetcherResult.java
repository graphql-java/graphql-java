package graphql.execution;

import com.google.common.collect.ImmutableList;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.schema.DataFetcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;


/**
 * An object that can be returned from a {@link DataFetcher} that contains both data, local context and errors to be added to the final result.
 * This is a useful when your ``DataFetcher`` retrieves data from multiple sources
 * or from another GraphQL resource, or you want to pass extra context to lower levels.
 * <p>
 * This also allows you to pass down new local context objects between parent and child fields.  If you return a
 * {@link #getLocalContext()} value then it will be passed down into any child fields via
 * {@link graphql.schema.DataFetchingEnvironment#getLocalContext()}
 * <p>
 * You can also have {@link DataFetcher}s contribute to the {@link ExecutionResult#getExtensions()} by returning
 * extensions maps that will be merged together via the {@link graphql.extensions.ExtensionsBuilder} and its {@link graphql.extensions.ExtensionsMerger}
 * in place.
 * <p>
 * This provides {@link #hashCode()} and {@link #equals(Object)} methods that afford comparison with other {@link DataFetcherResult} object.s
 * However, to function correctly, this relies on the values provided in the following fields in turn also implementing {@link #hashCode()}} and {@link #equals(Object)} as appropriate:
 * <ul>
 *   <li>The data returned in {@link #getData()}.
 *   <li>The individual errors returned in {@link #getErrors()}.
 *   <li>The context returned in {@link #getLocalContext()}.
 *   <li>The keys/values in the {@link #getExtensions()} {@link Map}.
 * </ul>
 *
 * @param <T> The type of the data fetched
 */
@PublicApi
@NullMarked
public class DataFetcherResult<T extends @Nullable Object> {

    private final @Nullable T data;
    private final List<GraphQLError> errors;
    private final @Nullable Object localContext;
    private final @Nullable Map<Object, Object> extensions;

    private DataFetcherResult(@Nullable T data, List<GraphQLError> errors, @Nullable Object localContext, @Nullable Map<Object, Object> extensions) {
        this.data = data;
        this.errors = ImmutableList.copyOf(assertNotNull(errors));
        this.localContext = localContext;
        this.extensions = extensions;
    }

    /**
     * @return The data fetched. May be null.
     */
    public @Nullable T getData() {
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
    public @Nullable Object getLocalContext() {
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
    public @Nullable Map<Object, Object> getExtensions() {
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
     * Transforms the data of the current DataFetcherResult using the provided function.
     * All other values are left unmodified.
     *
     * @param transformation the transformation that should be applied to the data
     * @param <R>            the result type
     *
     * @return a new instance with where the data value has been transformed
     */
    public <R> DataFetcherResult<R> map(Function<@Nullable T, @Nullable R> transformation) {
        return new Builder<>(transformation.apply(this.data))
                .errors(this.errors)
                .extensions(this.extensions)
                .localContext(this.localContext)
                .build();
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DataFetcherResult<?> that = (DataFetcherResult<?>) o;
        return Objects.equals(data, that.data)
               && errors.equals(that.errors)
               && Objects.equals(localContext, that.localContext)
               && Objects.equals(extensions, that.extensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, errors, localContext, extensions);
    }

    @Override
    public String toString() {
        return "DataFetcherResult{" +
               "data=" + data +
               ", errors=" + errors +
               ", localContext=" + localContext +
               ", extensions=" + extensions +
               '}';
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

    /**
     * Creates a new data fetcher result builder with associated data.
     * <p>Data may later be overwritten using {@link Builder#data(Object)}.
     *
     * @param data the data
     * @param <T> the type of the result
     *
     * @return a new builder
     */
    public static <T> Builder<@Nullable T> newResult(@Nullable T data) {
        return new Builder<>(data);
    }

    public static class Builder<T extends @Nullable Object> {
        private @Nullable T data;
        private @Nullable Object localContext;
        private final List<GraphQLError> errors = new ArrayList<>();
        private @Nullable Map<Object, Object> extensions;

        public Builder(DataFetcherResult<T> existing) {
            data = existing.getData();
            localContext = existing.getLocalContext();
            errors.addAll(existing.getErrors());
            extensions = existing.extensions;
        }

        public Builder(@Nullable T data) {
            this.data = data;
        }

        public Builder() {
        }

        public Builder<T> data(@Nullable T data) {
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

        public Builder<T> localContext(@Nullable Object localContext) {
            this.localContext = localContext;
            return this;
        }

        public Builder<T> extensions(@Nullable Map<Object, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public DataFetcherResult<T> build() {
            return new DataFetcherResult<>(data, errors, localContext, extensions);
        }
    }
}
