package graphql;

import graphql.execution.DataFetcherResult;
import graphql.execution.ResultPath;
import graphql.language.SourceLocation;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * This helps you build {@link graphql.GraphQLError}s and also has a quick way to make a  {@link graphql.execution.DataFetcherResult}s
 * from that error.
 *
 * @param <B> this base class allows you to derive new classes from this base error builder
 */
@SuppressWarnings("unchecked")
@PublicApi
public class GraphqlErrorBuilder<B extends GraphqlErrorBuilder<?>> {

    private String message;
    private List<Object> path;
    private List<SourceLocation> locations = new ArrayList<>();
    private ErrorClassification errorType = ErrorType.DataFetchingException;
    private Map<String, Object> extensions = null;

    public String getMessage() {
        return message;
    }

    public List<Object> getPath() {
        return path;
    }

    public List<SourceLocation> getLocations() {
        return locations;
    }

    public ErrorClassification getErrorType() {
        return errorType;
    }

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

    public B locations(List<SourceLocation> locations) {
        this.locations.addAll(assertNotNull(locations));
        return (B) this;
    }

    public B location(SourceLocation location) {
        this.locations.add(assertNotNull(location));
        return (B) this;
    }

    public B path(ResultPath path) {
        this.path = assertNotNull(path).toList();
        return (B) this;
    }

    public B path(List<Object> path) {
        this.path = assertNotNull(path);
        return (B) this;
    }

    public B errorType(ErrorClassification errorType) {
        this.errorType = assertNotNull(errorType);
        return (B) this;
    }

    public B extensions(Map<String, Object> extensions) {
        this.extensions = assertNotNull(extensions);
        return (B) this;
    }

    /**
     * @return a newly built GraphqlError
     */
    public GraphQLError build() {
        assertNotNull(message, () -> "You must provide error message");
        return new GraphqlErrorImpl(message, locations, errorType, path, extensions);
    }

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
