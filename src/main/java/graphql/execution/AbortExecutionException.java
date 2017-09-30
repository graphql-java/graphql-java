package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static graphql.Assert.assertNotNull;
import static java.util.Collections.emptyList;

/**
 * This Exception indicates that the current execution should be aborted.
 *
 * If a {@link graphql.schema.DataFetcher} throws this exception then
 * the execution of the query will be short circuited and any partial results
 * up until that point will be returned.
 */
@PublicApi
public class AbortExecutionException extends GraphQLException implements GraphQLError {

    private final List<GraphQLError> underlyingErrors;

    public AbortExecutionException() {
        this.underlyingErrors = emptyList();
    }

    public AbortExecutionException(Collection<GraphQLError> underlyingErrors) {
        this.underlyingErrors = new ArrayList<>(assertNotNull(underlyingErrors));
    }

    public AbortExecutionException(String message) {
        super(message);
        this.underlyingErrors = emptyList();
    }

    public AbortExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.underlyingErrors = emptyList();
    }

    public AbortExecutionException(Throwable cause) {
        super(cause);
        this.underlyingErrors = emptyList();
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ExecutionAborted;
    }

    /**
     * @return a list of underlying errors, which may be empty
     */
    public List<GraphQLError> getUnderlyingErrors() {
        return underlyingErrors;
    }
}
