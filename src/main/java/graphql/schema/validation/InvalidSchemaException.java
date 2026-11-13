package graphql.schema.validation;

import graphql.GraphQLException;
import graphql.Internal;
import graphql.VisibleForTesting;

import java.util.Collection;

@Internal
public class InvalidSchemaException extends GraphQLException {

    private final Collection<SchemaValidationError> errors;

    public InvalidSchemaException(Collection<SchemaValidationError> errors) {
        super(buildErrorMsg(errors));
        this.errors = errors;
    }

    @VisibleForTesting
    Collection<SchemaValidationError> getErrors() {
        return errors;
    }

    private static String buildErrorMsg(Collection<SchemaValidationError> errors) {
        StringBuilder message = new StringBuilder("invalid schema:");
        for (SchemaValidationError error : errors) {
            message.append("\n").append(error.getDescription());
        }
        return message.toString();
    }
}
