package graphql.validation;


import com.google.common.collect.ImmutableMap;
import graphql.DeprecatedAt;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.PublicApi;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@PublicApi
public class ValidationError implements GraphQLError {

    private final List<SourceLocation> locations = new ArrayList<>();
    private final String description;
    private final ValidationErrorClassification validationErrorType;
    private final List<String> queryPath = new ArrayList<>();
    private final ImmutableMap<String, Object> extensions;

    @Deprecated
    @DeprecatedAt("2022-07-10")
    public ValidationError(ValidationErrorClassification validationErrorType) {
        this(newValidationError()
                .validationErrorType(validationErrorType));
    }

    @Deprecated
    @DeprecatedAt("2022-07-10")
    public ValidationError(ValidationErrorClassification validationErrorType, SourceLocation sourceLocation, String description) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(sourceLocation)
                .description(description));
    }

    @Deprecated
    @DeprecatedAt("2022-07-10")
    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description, List<String> queryPath) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocation(sourceLocation)
                .description(description)
                .queryPath(queryPath));
    }

    @Deprecated
    @DeprecatedAt("2022-07-10")
    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(sourceLocations)
                .description(description));
    }

    @Deprecated
    @DeprecatedAt("2022-07-10")
    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description, List<String> queryPath) {
        this(newValidationError()
                .validationErrorType(validationErrorType)
                .sourceLocations(sourceLocations)
                .description(description)
                .queryPath(queryPath));
    }

    private ValidationError(Builder builder) {
        this.validationErrorType = builder.validationErrorType;
        this.description = builder.description;
        if (builder.sourceLocations != null) {
            this.locations.addAll(builder.sourceLocations);
        }

        if (builder.queryPath != null) {
            this.queryPath.addAll(builder.queryPath);
        }

        this.extensions = (builder.extensions != null) ? ImmutableMap.copyOf(builder.extensions) : ImmutableMap.of();
    }

    public ValidationErrorClassification getValidationErrorType() {
        return validationErrorType;
    }

    @Override
    public String getMessage() {
        return description;
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
        String extensionsString = "";

        if (extensions.size() > 0) {
            extensionsString = extensions
                    .keySet()
                    .stream()
                    .map(key -> key + "=" + extensions.get(key))
                    .collect(Collectors.joining(", "));
        }

        return "ValidationError{" +
                "validationErrorType=" + validationErrorType +
                ", queryPath=" + queryPath +
                ", message=" + description +
                ", locations=" + locations +
                ", description='" + description + '\'' +
                ", extensions=[" + extensionsString + ']' +
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
