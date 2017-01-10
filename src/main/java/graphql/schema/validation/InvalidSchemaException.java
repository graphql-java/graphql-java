package graphql.schema.validation;

import java.util.Collection;

import graphql.GraphQLException;

public class InvalidSchemaException extends GraphQLException {
    
    private final String message;
    
    public InvalidSchemaException(Collection<ValidationError> errors) {
        StringBuilder message = new StringBuilder("invalid schema:");
        for (ValidationError error : errors) {
            message.append("\n").append(error.getDescription());
        }
        this.message = message.toString();
    }

    @Override
    public String getMessage() {
        return message;
    }
}
