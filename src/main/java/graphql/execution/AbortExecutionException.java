package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.List;

@PublicApi
public class AbortExecutionException extends GraphQLException implements GraphQLError {

    public AbortExecutionException() {
    }

    public AbortExecutionException(String message) {
        super(message);
    }

    public AbortExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public AbortExecutionException(Throwable cause) {
        super(cause);
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ExecutionAborted;
    }
}
