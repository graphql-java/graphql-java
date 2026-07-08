package graphql.parser;


import graphql.GraphQLException;
import graphql.Internal;
import graphql.InvalidSyntaxError;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * This exception is thrown by the {@link Parser} if the graphql syntax is not valid
 */
@PublicApi
@NullMarked
public class InvalidSyntaxException extends GraphQLException {

    private final @Nullable String message;
    private final @Nullable String sourcePreview;
    private final @Nullable String offendingToken;
    private final @Nullable SourceLocation location;

    @Internal
    protected InvalidSyntaxException(@Nullable String msg, @Nullable SourceLocation location, @Nullable String offendingToken, @Nullable String sourcePreview, @Nullable Exception cause) {
        super(cause);
        this.message = msg;
        this.sourcePreview = sourcePreview;
        this.offendingToken = offendingToken;
        this.location = location;
    }

    public InvalidSyntaxError toInvalidSyntaxError() {
        @Nullable List<SourceLocation> sourceLocations = location == null ? null : Collections.singletonList(location);
        return new InvalidSyntaxError(sourceLocations, message, sourcePreview, offendingToken);
    }

    @Override
    public @Nullable String getMessage() {
        return message;
    }

    public @Nullable SourceLocation getLocation() {
        return location;
    }

    public @Nullable String getSourcePreview() {
        return sourcePreview;
    }

    public @Nullable String getOffendingToken() {
        return offendingToken;
    }

}

