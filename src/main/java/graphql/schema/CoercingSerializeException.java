package graphql.schema;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@PublicApi
public class CoercingSerializeException extends GraphQLException implements GraphQLError {
    private final List<SourceLocation> sourceLocations;
    private final Map<String, Object> extensions;

    public CoercingSerializeException() {
        this(newCoercingSerializeException());
    }

    public CoercingSerializeException(String message) {
        this(newCoercingSerializeException().message(message));
    }

    public CoercingSerializeException(String message, Throwable cause) {
        this(newCoercingSerializeException().message(message).cause(cause));
    }

    public CoercingSerializeException(Throwable cause) {
        this(newCoercingSerializeException().cause(cause));
    }

    private CoercingSerializeException(Builder builder) {
        super(builder.message, builder.cause);
        this.sourceLocations = builder.sourceLocations;
        this.extensions = builder.extensions;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public ErrorClassification getErrorType() {
        return ErrorType.DataFetchingException;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public static Builder newCoercingSerializeException() {
        return new Builder();
    }

    public static class Builder {
        private String message;
        private Throwable cause;
        private Map<String, Object> extensions;
        private List<SourceLocation> sourceLocations;

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

        public CoercingSerializeException build() {
            return new CoercingSerializeException(this);
        }
    }
}
