package graphql.schema;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLQueryException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;

@PublicApi
public class CoercingParseValueException extends GraphQLQueryException implements GraphQLError {
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
        this.sourceLocations = Collections.singletonList(sourceLocation);
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
