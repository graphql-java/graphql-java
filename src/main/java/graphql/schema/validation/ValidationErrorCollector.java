package graphql.schema.validation;

import java.util.LinkedHashSet;
import java.util.Set;

public class ValidationErrorCollector {

    private final LinkedHashSet<ValidationError> errors = new LinkedHashSet<>();

    public void addError(ValidationError validationError) {
        this.errors.add(validationError);
    }

    public Set<ValidationError> getErrors() {
        return errors;
    }

    public boolean containsValidationError(ValidationErrorType validationErrorType) {
        for (ValidationError validationError : errors) {
            if (validationError.getErrorType() == validationErrorType) return true;
        }
        return false;
    }
}
