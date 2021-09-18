package graphql.validation;


import graphql.Internal;

import java.util.ArrayList;
import java.util.List;

import static graphql.validation.ValidationErrorType.MaxValidationErrorsReached;

@Internal
public class ValidationErrorCollector {

    private final List<ValidationError> errors = new ArrayList<>();
    private final int maxErrors;

    public ValidationErrorCollector() {
        this(Validator.MAX_VALIDATION_ERRORS);
    }

    public ValidationErrorCollector(int maxErrors) {
        this.maxErrors = maxErrors;
    }

    private boolean atMaxErrors() {
        return errors.size() >= maxErrors - 1;
    }

    /**
     * This will throw {@link MaxValidationErrorsReached} if too many validation errors are added
     *
     * @param validationError the error to add
     *
     * @throws MaxValidationErrorsReached if too many errors have been generated
     */
    public void addError(ValidationError validationError) throws MaxValidationErrorsReached {
        if (!atMaxErrors()) {
            this.errors.add(validationError);
        } else {
            this.errors.add(ValidationError.newValidationError()
                    .validationErrorType(MaxValidationErrorsReached)
                    .description(
                            String.format("The maximum number of validation errors has been reached. (%d)", maxErrors)
                    )
                    .build());

            throw new MaxValidationErrorsReached();
        }
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public boolean containsValidationError(ValidationErrorType validationErrorType) {
        return containsValidationError(validationErrorType, null);
    }

    public boolean containsValidationError(ValidationErrorType validationErrorType, String description) {
        for (ValidationError validationError : errors) {
            if (validationError.getValidationErrorType() == validationErrorType) {
                return description == null || validationError.getDescription().equals(description);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ValidationErrorCollector{" +
                "errors=" + errors +
                '}';
    }

    /**
     * Indicates that that maximum number of validation errors has been reached
     */
    @Internal
    static class MaxValidationErrorsReached extends RuntimeException {

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

}
