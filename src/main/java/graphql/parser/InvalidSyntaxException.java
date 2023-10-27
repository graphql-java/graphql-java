package graphql.parser;


import graphql.GraphQLException;
import graphql.Internal;
import graphql.InvalidSyntaxError;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;

/**
 * This exception is thrown by the {@link Parser} if the graphql syntax is not valid
 */
@PublicApi
public class InvalidSyntaxException extends GraphQLException {

    private final String message;
    private final String sourcePreview;
    private final String offendingToken;
    private final SourceLocation location;

    @Internal
    protected InvalidSyntaxException(String msg, SourceLocation location, String offendingToken, String sourcePreview, Exception cause) {
        super(cause);
        this.message = msg;
        this.sourcePreview = sourcePreview;
        this.offendingToken = offendingToken;
        this.location = location;
    }

    public InvalidSyntaxError toInvalidSyntaxError() {
        List<SourceLocation> sourceLocations = location == null ? null : Collections.singletonList(location);
        return new InvalidSyntaxError(sourceLocations, message, sourcePreview, offendingToken);
    }

    @Override
    public String getMessage() {
        return message;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public String getSourcePreview() {
        return sourcePreview;
    }

    public String getOffendingToken() {
        return offendingToken;
    }

}

