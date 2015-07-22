package graphql.validation;


import graphql.GraphQLError;

public class ValidationError implements GraphQLError {

    private final ValidationErrorType errorType;

    public ValidationError(ValidationErrorType errorType) {
        this.errorType = errorType;
    }

    public ValidationErrorType getErrorType() {
        return errorType;
    }

    //    private final String description;

//    public ValidationError(String description, Object... args) {
//        this.description = String.format(description, args);
//    }

//    public String getDescription() {
//        return description;
//    }
//
//    @Override
//    public String toString() {
//        return "ValidationError{" +
//                "description='" + description + '\'' +
//                '}';
//    }
}
