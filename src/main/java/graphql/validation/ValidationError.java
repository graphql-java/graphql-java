package graphql.validation;


import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ValidationError implements GraphQLError {


    private final ValidationErrorType validationErrorType;
    private final List<SourceLocation> sourceLocations = new ArrayList<SourceLocation>();
    private final String description;

    public ValidationError(ValidationErrorType validationErrorType) {
        this(validationErrorType, (SourceLocation) null, null);
    }

    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description) {
        this.validationErrorType = validationErrorType;
        if (sourceLocation != null)
            this.sourceLocations.add(sourceLocation);
        this.description = description;
    }

    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description) {
        this.validationErrorType = validationErrorType;
        if (sourceLocations != null)
            this.sourceLocations.addAll(sourceLocations);
        this.description = description;
    }

    public ValidationErrorType getValidationErrorType() {
        return validationErrorType;
    }

    @Override
    public String getMessage() {
        return String.format("Validation error of type %s: %s", validationErrorType, description);
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
    public String toString() {
        return "ValidationError{" +
                "validationErrorType=" + validationErrorType +
                ", sourceLocations=" + sourceLocations +
                ", description='" + description + '\'' +
                '}';
    }
}
