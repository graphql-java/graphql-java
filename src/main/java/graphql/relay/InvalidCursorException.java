package graphql.relay;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.List;

import static graphql.ErrorType.DataFetchingException;

public class InvalidCursorException extends RuntimeException implements GraphQLError {

    InvalidCursorException(String message) {
        this(message, null);
    }

    InvalidCursorException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return DataFetchingException;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return Helper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Helper.hashCode(this);
    }

}
