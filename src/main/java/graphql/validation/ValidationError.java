package graphql.validation;


import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>ValidationError class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ValidationError implements GraphQLError {


    private final ValidationErrorType validationErrorType;
    private final List<SourceLocation> sourceLocations = new ArrayList<>();
    private final String description;

    /**
     * <p>Constructor for ValidationError.</p>
     *
     * @param validationErrorType a {@link graphql.validation.ValidationErrorType} object.
     */
    public ValidationError(ValidationErrorType validationErrorType) {
        this(validationErrorType, (SourceLocation) null, null);
    }

    /**
     * <p>Constructor for ValidationError.</p>
     *
     * @param validationErrorType a {@link graphql.validation.ValidationErrorType} object.
     * @param sourceLocation a {@link graphql.language.SourceLocation} object.
     * @param description a {@link java.lang.String} object.
     */
    public ValidationError(ValidationErrorType validationErrorType, SourceLocation sourceLocation, String description) {
        this.validationErrorType = validationErrorType;
        if (sourceLocation != null)
            this.sourceLocations.add(sourceLocation);
        this.description = description;
    }

    /**
     * <p>Constructor for ValidationError.</p>
     *
     * @param validationErrorType a {@link graphql.validation.ValidationErrorType} object.
     * @param sourceLocations a {@link java.util.List} object.
     * @param description a {@link java.lang.String} object.
     */
    public ValidationError(ValidationErrorType validationErrorType, List<SourceLocation> sourceLocations, String description) {
        this.validationErrorType = validationErrorType;
        if (sourceLocations != null)
            this.sourceLocations.addAll(sourceLocations);
        this.description = description;
    }

    /**
     * <p>Getter for the field <code>validationErrorType</code>.</p>
     *
     * @return a {@link graphql.validation.ValidationErrorType} object.
     */
    public ValidationErrorType getValidationErrorType() {
        return validationErrorType;
    }

    /** {@inheritDoc} */
    @Override
    public String getMessage() {
        return String.format("Validation error of type %s: %s", validationErrorType, description);
    }

    /** {@inheritDoc} */
    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    /** {@inheritDoc} */
    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ValidationError{" +
                "validationErrorType=" + validationErrorType +
                ", sourceLocations=" + sourceLocations +
                ", description='" + description + '\'' +
                '}';
    }
}
