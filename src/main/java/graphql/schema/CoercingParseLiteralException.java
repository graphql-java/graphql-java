package graphql.schema;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLQueryException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;

@PublicApi
public class CoercingParseLiteralException extends GraphQLQueryException implements GraphQLError {
    private List<SourceLocation> sourceLocations;

    public CoercingParseLiteralException() {
    }

    public CoercingParseLiteralException(String message) {
        super(message);
    }

    public CoercingParseLiteralException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoercingParseLiteralException(String message, Throwable cause, SourceLocation sourceLocation) {
        super(message, cause);
        this.sourceLocations = Collections.singletonList(sourceLocation);
    }

    public CoercingParseLiteralException(Throwable cause) {
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
