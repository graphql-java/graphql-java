package graphql.schema;

import graphql.ErrorType;
import graphql.GraphqlErrorException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

@PublicApi
public class CoercingLiteralToValueException extends GraphqlErrorException {

    public CoercingLiteralToValueException() {
        this(newCoercingLiteralToValueException());
    }

    public CoercingLiteralToValueException(String message) {
        this(newCoercingLiteralToValueException().message(message));
    }

    public CoercingLiteralToValueException(String message, Throwable cause) {
        this(newCoercingLiteralToValueException().message(message).cause(cause));
    }

    public CoercingLiteralToValueException(String message, Throwable cause, SourceLocation sourceLocation) {
        this(newCoercingLiteralToValueException().message(message).cause(cause).sourceLocation(sourceLocation));
    }

    public CoercingLiteralToValueException(Throwable cause) {
        this(newCoercingLiteralToValueException().cause(cause));
    }

    private CoercingLiteralToValueException(Builder builder) {
        super(builder);
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    public static Builder newCoercingLiteralToValueException() {
        return new Builder();
    }

    public static class Builder extends BuilderBase<Builder, CoercingLiteralToValueException> {
        public CoercingLiteralToValueException build() {
            return new CoercingLiteralToValueException(this);
        }
    }
}
