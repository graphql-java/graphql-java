package graphql.validation;


import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@PublicApi
public class ValidationError implements GraphQLError {

    private final String message;
    private final List<SourceLocation> locations = new ArrayList<>();
    private final String description;
    private final ValidationErrorClassification validationErrorType;
    private final List<String> queryPath;
    private final Map<String, Object> extensions;

    public ValidationError(ValidationErrorClassification validationErrorType) {
        this(newValidationError()
                .validationErrorType(validationErrorType));
    }

    public ValidationError(ValidationErrorClassification validationErrorType, SourceLocation sourceLocation, String description) {
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

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(sourceLocations)
                .description(description));
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description, List<String> queryPath) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(sourceLocations)
                .description(description)
                .queryPath(queryPath));
    }

    private ValidationError(Builder builder) {
        this.validationErrorType = builder.validationErrorType;
        if (builder.sourceLocations != null) {
            this.locations.addAll(builder.sourceLocations);
        }
        this.description = builder.description;
        this.message = mkMessage(builder.validationErrorType, builder.description, builder.queryPath);
        this.queryPath = builder.queryPath;
        this.extensions = builder.extensions;
    }

    private String mkMessage(ValidationErrorClassification validationErrorType, String description, List<String> queryPath) {
        return String.format("Validation error of type %s: %s%s", validationErrorType, description, toPath(queryPath));
    }

    private String toPath(List<String> queryPath) {
        if (queryPath == null) {
            return "";
        }
        return String.format(" @ '%s'", String.join("/", queryPath));
    }

    public ValidationErrorClassification getValidationErrorType() {
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
        private Map<String, Object> extensions;
        private String description;
        private ValidationErrorClassification validationErrorType;
        private List<String> queryPath;


        public Builder validationErrorType(ValidationErrorClassification validationErrorType) {
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

        public ValidationError build() {
            return new ValidationError(this);
        }
    }
}
