package graphql;

import graphql.execution.DataFetcherResult;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import graphql.schema.DataFetchingEnvironment;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static graphql.Assert.assertNotNull;

/**
 * This helps you build {@link graphql.GraphQLError}s and also has a quick way to make a  {@link graphql.execution.DataFetcherResult}s
 * from that error.
 *
 * @param <B> this base class allows you to derive new classes from this base error builder
 */
@SuppressWarnings("unchecked")
@PublicApi
@NullUnmarked
public class GraphqlErrorBuilder<B extends GraphqlErrorBuilder<B>> implements GraphQLError.Builder<B> {

    private String message;
    private List<Object> path;
    private List<SourceLocation> locations = new ArrayList<>();
    private ErrorClassification errorType = ErrorType.DataFetchingException;
    private Map<String, Object> extensions = null;

    public String getMessage() {
        return message;
    }

    @Nullable
    public List<Object> getPath() {
        return path;
    }

    @Nullable
    public List<SourceLocation> getLocations() {
        return locations;
    }

    public ErrorClassification getErrorType() {
        return errorType;
    }

    @Nullable
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * @return a builder of {@link graphql.GraphQLError}s
     */
    public static GraphqlErrorBuilder<?> newError() {
        return new GraphqlErrorBuilder<>();
    }

    /**
     * This will set up the {@link GraphQLError#getLocations()} and {@link graphql.GraphQLError#getPath()} for you from the
     * fetching environment.
     *
     * @param dataFetchingEnvironment the data fetching environment
     *
     * @return a builder of {@link graphql.GraphQLError}s
     */
    public static GraphqlErrorBuilder<?> newError(DataFetchingEnvironment dataFetchingEnvironment) {
        return new GraphqlErrorBuilder<>()
                .location(dataFetchingEnvironment.getField().getSourceLocation())
                .path(dataFetchingEnvironment.getExecutionStepInfo().getPath());
    }

    protected GraphqlErrorBuilder() {
    }

    public B message(String message, Object... formatArgs) {
        if (formatArgs == null || formatArgs.length == 0) {
            this.message = assertNotNull(message);
        } else {
            this.message = String.format(assertNotNull(message), formatArgs);
        }
        return (B) this;
    }

    public B locations(@Nullable List<SourceLocation> locations) {
        if (locations != null) {
            this.locations.addAll(locations);
        } else {
            this.locations = null;
        }
        return (B) this;
    }

    public B location(@Nullable SourceLocation location) {
        if (locations != null) {
            this.locations.add(location);
        }
        return (B) this;
    }

    public B path(@Nullable ResultPath path) {
        if (path != null) {
            this.path = path.toList();
        } else {
            this.path = null;
        }
        return (B) this;
    }

    public B path(@Nullable List<Object> path) {
        this.path = path;
        return (B) this;
    }

    public B errorType(ErrorClassification errorType) {
        this.errorType = assertNotNull(errorType);
        return (B) this;
    }

    public B extensions(@Nullable Map<String, Object> extensions) {
        this.extensions = extensions;
        return (B) this;
    }

    /**
     * @return a newly built GraphqlError
     */
    public GraphQLError build() {
        assertNotNull(message, "You must provide error message");
        return new GraphqlErrorImpl(message, locations, errorType, path, extensions);
    }

    /**
     * A simple implementation of a {@link GraphQLError}.
     * <p>
     * This provides {@link #hashCode()} and {@link #equals(Object)} methods that afford comparison with other
     * {@link GraphQLError} implementations. However, the values provided in the following fields <b>must</b>
     * in turn implement {@link #hashCode()} and {@link #equals(Object)} for this to function correctly:
     * <ul>
     *   <li>the values in the {@link #getPath()} {@link List}.
     *   <li>the {@link #getErrorType()} {@link ErrorClassification}.
     *   <li>the values in the {@link #getExtensions()} {@link Map}.
     * </ul>
     */
    private static class GraphqlErrorImpl implements GraphQLError {
        private final String message;
        private final List<SourceLocation> locations;
        private final ErrorClassification errorType;
        private final List<Object> path;
        private final Map<String, Object> extensions;

        public GraphqlErrorImpl(String message, List<SourceLocation> locations, ErrorClassification errorType, List<Object> path, Map<String, Object> extensions) {
            this.message = message;
            this.locations = locations;
            this.errorType = errorType;
            this.path = path;
            this.extensions = extensions;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public List<SourceLocation> getLocations() {
            return locations;
        }

        @Override
        public ErrorClassification getErrorType() {
            return errorType;
        }

        @Override
        public List<Object> getPath() {
            return path;
        }

        @Override
        public Map<String, Object> getExtensions() {
            return extensions;
        }

        @Override
        public String toString() {
            return message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GraphQLError)) return false;
            GraphQLError that = (GraphQLError) o;
            return Objects.equals(getMessage(), that.getMessage())
                    && Objects.equals(getLocations(), that.getLocations())
                    && Objects.equals(getErrorType(), that.getErrorType())
                    && Objects.equals(getPath(), that.getPath())
                    && Objects.equals(getExtensions(), that.getExtensions());
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getMessage(),
                    getLocations(),
                    getErrorType(),
                    getPath(),
                    getExtensions());
        }
    }

    /**
     * A helper method that allows you to return this error as a {@link graphql.execution.DataFetcherResult}
     *
     * @return a new data fetcher result that contains the built error
     */
    public DataFetcherResult<?> toResult() {
        return DataFetcherResult.newResult()
                .error(build())
                .build();
    }

}
