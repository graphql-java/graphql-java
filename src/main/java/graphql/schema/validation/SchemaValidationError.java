package graphql.schema.validation;

import graphql.Internal;

import java.util.StringJoiner;

import static graphql.Assert.assertNotNull;

@Internal
public class SchemaValidationError {

    private final SchemaValidationErrorClassification errorClassification;
    private final String description;

    public SchemaValidationError(SchemaValidationErrorType errorClassification, String description) {
        assertNotNull(errorClassification, () -> "error classification can not be null");
        assertNotNull(description, () -> "error description can not be null");
        this.errorClassification = errorClassification;
        this.description = description;
    }

    public SchemaValidationErrorClassification getClassification() {
        return errorClassification;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SchemaValidationError.class.getSimpleName() + "[", "]")
                .add("errorClassification=" + errorClassification)
                .add("description='" + description + "'")
                .toString();
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + errorClassification.hashCode();
        result = 31 * result + description.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SchemaValidationError)) {
            return false;
        }
        SchemaValidationError that = (SchemaValidationError) other;
        return this.errorClassification.equals(that.errorClassification) && this.description.equals(that.description);
    }
}
