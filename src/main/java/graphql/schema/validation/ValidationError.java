package graphql.schema.validation;

import static graphql.Assert.assertNotNull;

public class ValidationError {

    private final ValidationErrorType errorType;
    private final String description;

    public ValidationError(ValidationErrorType errorType, String description) {
        assertNotNull(errorType, "error type can not be null");
        assertNotNull(description, "error description can not be null");
        this.errorType = errorType;
        this.description = description;
    }

    public ValidationErrorType getErrorType() {
        return errorType;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public int hashCode() {
        return errorType.hashCode() ^ description.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ValidationError)) {
            return false;
        }
        ValidationError that = (ValidationError) other;
        return this.errorType.equals(that.errorType) && this.description.equals(that.description);
    }
}
