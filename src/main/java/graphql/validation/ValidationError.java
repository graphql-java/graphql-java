package graphql.validation;


public class ValidationError {

    private final String description;

    public ValidationError(String description, Object... args) {
        this.description = String.format(description, args);
    }

    @Override
    public String toString() {
        return "ValidationError{" +
                "description='" + description + '\'' +
                '}';
    }
}
