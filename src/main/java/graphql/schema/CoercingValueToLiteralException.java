package graphql.schema;

import graphql.ErrorType;
import graphql.GraphqlErrorException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

@PublicApi
public class CoercingValueToLiteralException extends GraphqlErrorException {

    public CoercingValueToLiteralException() {
        this(newCoercingValueToLiteralException());
    }

    public CoercingValueToLiteralException(String message) {
        this(newCoercingValueToLiteralException().message(message));
    }

    public CoercingValueToLiteralException(String message, Throwable cause) {
        this(newCoercingValueToLiteralException().message(message).cause(cause));
    }

    public CoercingValueToLiteralException(String message, Throwable cause, SourceLocation sourceLocation) {
        this(newCoercingValueToLiteralException().message(message).cause(cause).sourceLocation(sourceLocation));
    }

    public CoercingValueToLiteralException(Throwable cause) {
        this(newCoercingValueToLiteralException().cause(cause));
    }

    private CoercingValueToLiteralException(Builder builder) {
        super(builder);
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    public static Builder newCoercingValueToLiteralException() {
        return new Builder();
    }

    public static class Builder extends BuilderBase<Builder, CoercingValueToLiteralException> {
        public CoercingValueToLiteralException build() {
            return new CoercingValueToLiteralException(this);
        }
    }
}
