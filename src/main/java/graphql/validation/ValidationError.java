package graphql.validation;


import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

public class ValidationError implements GraphQLError {


    private final ValidationErrorType validationErrorType;
    private final SourceLocation sourceLocation;
    private final String description;

    public ValidationError(ValidationErrorType validationErrorType) {
        this(validationErrorType, null, null);
    }

    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description) {
        this.validationErrorType = validationErrorType;
        this.sourceLocation = sourceLocation;
        this.description = description;
    }

    public ValidationErrorType getValidationErrorType() {
        return validationErrorType;
    }

    @Override
    public ErrorType geErrorType() {
        return ErrorType.ValidationError;
    }

    @Override
    public String toString() {
        return "ValidationError{" +
                "validationErrorType=" + validationErrorType +
                ", sourceLocation=" + sourceLocation +
                ", description='" + description + '\'' +
                '}';
    }
}
