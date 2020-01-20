package graphql.schema;

import graphql.ErrorType;
import graphql.GraphqlErrorException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

@PublicApi
public class CoercingParseValueException extends GraphqlErrorException {

    public CoercingParseValueException() {
        this(newCoercingParseValueException());
    }

    public CoercingParseValueException(String message) {
        this(newCoercingParseValueException().message(message));
    }

    public CoercingParseValueException(String message, Throwable cause) {
        this(newCoercingParseValueException().message(message).cause(cause));
    }

    public CoercingParseValueException(Throwable cause) {
        this(newCoercingParseValueException().cause(cause));
    }

    public CoercingParseValueException(String message, Throwable cause, SourceLocation sourceLocation) {
        this(newCoercingParseValueException().message(message).cause(cause).sourceLocation(sourceLocation));
    }

    private CoercingParseValueException(Builder builder) {
        super(builder);
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    public static Builder newCoercingParseValueException() {
        return new Builder();
    }

    public static class Builder extends BuilderBase<Builder,CoercingParseValueException> {
        public CoercingParseValueException build() {
            return new CoercingParseValueException(this);
        }
    }
}
