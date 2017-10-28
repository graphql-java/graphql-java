package graphql.schema;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.language.SourceLocation;

import java.util.List;

public class CoercingParseValueException extends GraphQLException implements GraphQLError {

    public CoercingParseValueException() {
    }

    public CoercingParseValueException(String message) {
        super(message);
    }

    public CoercingParseValueException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoercingParseValueException(Throwable cause) {
        super(cause);
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }
}
