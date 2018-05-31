package graphql.schema.validation;

import graphql.GraphQLInstanceException;

import java.util.Collection;

public class InvalidSchemaException extends GraphQLInstanceException {

    public InvalidSchemaException(Collection<SchemaValidationError> errors) {
        super(buildErrorMsg(errors));
    }

    private static String buildErrorMsg(Collection<SchemaValidationError> errors) {
        StringBuilder message = new StringBuilder("invalid schema:");
        for (SchemaValidationError error : errors) {
            message.append("\n").append(error.getDescription());
        }
        return message.toString();
    }
}
