package graphql.schema.validation.exception;

import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorType;

import java.util.LinkedHashSet;
import java.util.Set;

public class SchemaValidationErrorCollector {

    private final LinkedHashSet<SchemaValidationError> errors = new LinkedHashSet<>();

    public void addError(SchemaValidationError validationError) {
        this.errors.add(validationError);
    }

    public Set<SchemaValidationError> getErrors() {
        return errors;
    }

    public boolean containsValidationError(SchemaValidationErrorType validationErrorType) {
        for (SchemaValidationError validationError : errors) {
            if (validationError.getErrorType() == validationErrorType) return true;
        }
        return false;
    }
}
