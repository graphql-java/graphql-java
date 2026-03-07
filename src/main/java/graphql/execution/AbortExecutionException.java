package graphql.execution;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static graphql.Assert.assertNotNull;
import static graphql.collect.ImmutableKit.emptyList;

/**
 * This Exception indicates that the current execution should be aborted.
 */
@PublicApi
@NullMarked
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
    public @Nullable List<SourceLocation> getLocations() {
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

    /**
     * This is useful for turning this abort signal into an execution result which
     * is an error state with the underlying errors in it.
     *
     * @return an execution result with the errors from this exception
     */
    public ExecutionResult toExecutionResult() {
        if (!this.getUnderlyingErrors().isEmpty()) {
            return new ExecutionResultImpl(this.getUnderlyingErrors());
        }

        return new ExecutionResultImpl(this);
    }
}
