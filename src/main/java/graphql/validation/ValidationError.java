package graphql.validation;


import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationError implements GraphQLError {

    private final String message;
    private final List<SourceLocation> locations = new ArrayList<>();
    private final String description;
    private final ValidationErrorType validationErrorType;
    private final List<Object> path;

    public ValidationError(ValidationErrorType validationErrorType) {
        this(validationErrorType, (SourceLocation) null, null);
    }

    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description) {
        this(validationErrorType, nullOrList(sourceLocation), description, null);
    }

    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description, List<Object> path) {
        this(validationErrorType, nullOrList(sourceLocation), description, path);
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description) {
        this(validationErrorType, sourceLocations, description, null);
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description, List<Object> path) {
        this.validationErrorType = validationErrorType;
        if (sourceLocations != null)
            this.locations.addAll(sourceLocations);
        this.description = description;
        this.message = mkMessage(validationErrorType, description);
        this.path = path;
    }

    private static List<SourceLocation> nullOrList(SourceLocation sourceLocation) {
        return sourceLocation == null ? null : Collections.singletonList(sourceLocation);
    }

    private String mkMessage(ValidationErrorType validationErrorType, String description) {
        return String.format("Validation error of type %s: %s", validationErrorType, description);
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
    public List<Object> getPath() {
        return path;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }


    @Override
    public String toString() {
        return "ValidationError{" +
                "validationErrorType=" + validationErrorType +
                ", path=" + path +
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

}
