package graphql.schema;

import graphql.ErrorType;
import graphql.GraphqlErrorException;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

@PublicApi
@NullMarked
public class CoercingParseLiteralException extends GraphqlErrorException {

    public CoercingParseLiteralException() {
        this(newCoercingParseLiteralException());
    }

    public CoercingParseLiteralException(String message) {
        this(newCoercingParseLiteralException().message(message));
    }

    public CoercingParseLiteralException(String message, Throwable cause) {
        this(newCoercingParseLiteralException().message(message).cause(cause));
    }

    public CoercingParseLiteralException(String message, @Nullable Throwable cause, @Nullable SourceLocation sourceLocation) {
        this(newCoercingParseLiteralException().message(message).cause(cause).sourceLocation(sourceLocation));
    }

    public CoercingParseLiteralException(Throwable cause) {
        this(newCoercingParseLiteralException().cause(cause));
    }

    private CoercingParseLiteralException(Builder builder) {
        super(builder);
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    public static Builder newCoercingParseLiteralException() {
        return new Builder();
    }

    @NullUnmarked
    public static class Builder extends BuilderBase<Builder, CoercingParseLiteralException> {
        public CoercingParseLiteralException build() {
            return new CoercingParseLiteralException(this);
        }
    }
}
