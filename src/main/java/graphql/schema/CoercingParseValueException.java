package graphql.schema;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.Arrays;
import java.util.List;

@PublicApi
public class CoercingParseValueException extends GraphQLException implements GraphQLError {
    private List<SourceLocation> sourceLocations;

    public CoercingParseValueException() {
    }

    public CoercingParseValueException(String message) {
        super(message);
    }

    public CoercingParseValueException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoercingParseValueException(String message, Throwable cause, SourceLocation sourceLocation) {
        super(message, cause);
        this.sourceLocations = Arrays.asList(sourceLocation);
    }

    public CoercingParseValueException(Throwable cause) {
        super(cause);
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }
}
