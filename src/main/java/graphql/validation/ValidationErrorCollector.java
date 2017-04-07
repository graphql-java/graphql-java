package graphql.validation;


import java.util.ArrayList;
import java.util.List;

public class ValidationErrorCollector {

    private final List<ValidationError> errors = new ArrayList<>();

    public void addError(ValidationError validationError) {
        this.errors.add(validationError);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public boolean containsValidationError(ValidationErrorType validationErrorType) {
        for (ValidationError validationError : errors) {
            if (validationError.getValidationErrorType() == validationErrorType) return true;
        }
        return false;
    }
}
