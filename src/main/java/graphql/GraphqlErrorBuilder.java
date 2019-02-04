package graphql;

import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionPath;
import graphql.language.SourceLocation;
import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * This helps you build {@link graphql.GraphQLError}s and also has a quick way to make a  {@link graphql.execution.DataFetcherResult}s
 * from that error.
 */
@PublicApi
public class GraphqlErrorBuilder {

    private String message;
    private List<Object> path;
    private List<SourceLocation> locations = new ArrayList<>();
    private ErrorType errorType = ErrorType.DataFetchingException;
    private Map<String, Object> extensions = null;


    /**
     * @return a builder of {@link graphql.GraphQLError}s
     */
    public static GraphqlErrorBuilder newError() {
        return new GraphqlErrorBuilder();
    }

    /**
     * This will set up the {@link GraphQLError#getLocations()} and {@link graphql.GraphQLError#getPath()} for you from the
     * fetching environment.
     *
     * @param dataFetchingEnvironment the data fetching environment
     *
     * @return a builder of {@link graphql.GraphQLError}s
     */
    public static GraphqlErrorBuilder newError(DataFetchingEnvironment dataFetchingEnvironment) {
        return new GraphqlErrorBuilder()
                .location(dataFetchingEnvironment.getField().getSourceLocation())
                .path(dataFetchingEnvironment.getExecutionStepInfo().getPath());
    }

    private GraphqlErrorBuilder() {
    }

    public GraphqlErrorBuilder message(String message, Object... formatArgs) {
        this.message = String.format(assertNotNull(message), formatArgs);
        return this;
    }

    public GraphqlErrorBuilder locations(List<SourceLocation> locations) {
        this.locations.addAll(assertNotNull(locations));
        return this;
    }

    public GraphqlErrorBuilder location(SourceLocation location) {
        this.locations.add(assertNotNull(location));
        return this;
    }

    public GraphqlErrorBuilder path(ExecutionPath path) {
        this.path = assertNotNull(path).toList();
        return this;
    }

    public GraphqlErrorBuilder path(List<Object> path) {
        this.path = assertNotNull(path);
        return this;
    }

    public GraphqlErrorBuilder errorType(ErrorType errorType) {
        this.errorType = assertNotNull(errorType);
        return this;
    }

    public GraphqlErrorBuilder extensions(Map<String, Object> extensions) {
        this.extensions = assertNotNull(extensions);
        return this;
    }

    /**
     * @return a newly built GraphqlError
     */
    public GraphQLError build() {
        assertNotNull(message, "You must provide error message");
        return new GraphQLError() {
            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public List<SourceLocation> getLocations() {
                return locations;
            }

            @Override
            public ErrorType getErrorType() {
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
        };
    }

    /**
     * A helper method that allows you to return this error as a {@link graphql.execution.DataFetcherResult}
     *
     * @return a new data fetcher result that contains the built error
     */
    public DataFetcherResult toResult() {
        return DataFetcherResult.newResult()
                .error(build())
                .build();
    }

}
