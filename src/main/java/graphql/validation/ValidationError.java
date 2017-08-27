package graphql.validation;


import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ValidationError implements GraphQLError {

    private final String message;
    private final List<SourceLocation> locations = new ArrayList<>();
    private final String description;
    private final ValidationErrorType validationErrorType;

    public ValidationError(ValidationErrorType validationErrorType) {
        this(validationErrorType, (SourceLocation) null, null);
    }

    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description) {
        this.validationErrorType = validationErrorType;
        if (sourceLocation != null)
            this.locations.add(sourceLocation);
        this.description = description;
        this.message = mkMessage(validationErrorType, description);
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description) {
        this.validationErrorType = validationErrorType;
        if (sourceLocations != null)
            this.locations.addAll(sourceLocations);
        this.description = description;
        this.message = mkMessage(validationErrorType, description);
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
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }


    @Override
    public String toString() {
        return "ValidationError{" +
                "validationErrorType=" + validationErrorType +
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
