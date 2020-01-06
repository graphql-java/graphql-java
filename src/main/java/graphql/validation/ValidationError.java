package graphql.validation;


import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.language.SourceLocation;

import java.util.*;

public class ValidationError implements GraphQLError {

    private final String message;
    private final List<SourceLocation> locations = new ArrayList<>();
    private final String description;
    private final ValidationErrorType validationErrorType;
    private final List<String> queryPath;
    private final Map<String, Object> extensions;

    public ValidationError(ValidationErrorType validationErrorType) {
        this(newValidationError()
                .validationErrorType(validationErrorType));
    }

    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(sourceLocation)
                .description(description));
    }

    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description, List<String> queryPath) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(sourceLocation)
                .description(description)
                .queryPath(queryPath));
    }

    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description, List<String> queryPath, Map<String, Object> extensions) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(sourceLocation)
                .description(description)
                .queryPath(queryPath)
                .extensions(extensions));
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(sourceLocations)
                .description(description));
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description, List<String> queryPath) {
        this(validationErrorType, sourceLocations, description, queryPath, Collections.emptyMap());
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description, List<String> queryPath, Map<String, Object> extensions) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(sourceLocations)
                .description(description)
                .queryPath(queryPath)
                .extensions(extensions));
    }

    private ValidationError(Builder builder) {
        this.validationErrorType = builder.validationErrorType;
        if (builder.sourceLocations != null) {
            this.locations.addAll(builder.sourceLocations);
        }
        this.description = builder.description;
        this.message = builder.description;
        this.queryPath = builder.queryPath;

        builder.addExtension("validationErrorType", validationErrorType.name());
        if (builder.queryPath != null) {
            builder.addExtension("queryPath", builder.queryPath);
        }
        this.extensions = builder.extensions;
    }

    public ValidationErrorType getValidationErrorType() {
        return validationErrorType;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return locations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }

    public List<String> getQueryPath() {
        return queryPath;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    @Override
    public String toString() {
        return "ValidationError{" +
                "validationErrorType=" + validationErrorType +
                ", queryPath=" + queryPath +
                ", message=" + message +
                ", locations=" + locations +
                ", description='" + description + '\'' +
                '}';
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return GraphqlErrorHelper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return GraphqlErrorHelper.hashCode(this);
    }


    public static Builder newValidationError() {
        return new Builder();
    }

    public static class Builder {
        private List<SourceLocation> sourceLocations;
        private Map<String, Object> extensions = new LinkedHashMap<>();
        private String description;
        private ValidationErrorType validationErrorType;
        private List<String> queryPath;


        public Builder validationErrorType(ValidationErrorType validationErrorType) {
            this.validationErrorType = validationErrorType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder queryPath(List<String> queryPath) {
            this.queryPath = queryPath;
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

        public Builder addExtension(String key, Object value) {
            extensions.put(key, value);
            return this;
        }

        public ValidationError build() {
            return new ValidationError(this);
        }
    }
}
