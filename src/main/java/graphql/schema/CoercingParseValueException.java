package graphql.schema;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@PublicApi
public class CoercingParseValueException extends GraphQLException implements GraphQLError {
    private final List<SourceLocation> sourceLocations;
    private final Map<String, Object> extensions;

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
        super(builder.message, builder.cause);
        this.sourceLocations = builder.sourceLocations;
        this.extensions = builder.extensions;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public static Builder newCoercingParseValueException() {
        return new Builder();
    }

    public static class Builder {
        private String message;
        private Throwable cause;
        private List<SourceLocation> sourceLocations;
        private Map<String, Object> extensions;

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocations = sourceLocation == null ? null : Collections.singletonList(sourceLocation);
            return this;
        }

        public Builder sourceLocations(List<SourceLocation> sourceLocations) {
            this.sourceLocations = sourceLocations;
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public CoercingParseValueException build() {
            return new CoercingParseValueException(this);
        }
    }
}
