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
public class CoercingParseLiteralException extends GraphQLException implements GraphQLError {
    private final List<SourceLocation> sourceLocations;
    private final Map<String, Object> extensions;

    public CoercingParseLiteralException() {
        this(newCoercingParseLiteralException());
    }

    public CoercingParseLiteralException(String message) {
        this(newCoercingParseLiteralException().message(message));
    }

    public CoercingParseLiteralException(String message, Throwable cause) {
        this(newCoercingParseLiteralException().message(message).cause(cause));
    }

    public CoercingParseLiteralException(String message, Throwable cause, SourceLocation sourceLocation) {
        this(newCoercingParseLiteralException().message(message).cause(cause).sourceLocation(sourceLocation));
    }

    public CoercingParseLiteralException(Throwable cause) {
        this(newCoercingParseLiteralException().cause(cause));
    }

    private CoercingParseLiteralException(Builder builder) {
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

    public static Builder newCoercingParseLiteralException() {
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

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public CoercingParseLiteralException build() {
            return new CoercingParseLiteralException(this);
        }
    }
}
