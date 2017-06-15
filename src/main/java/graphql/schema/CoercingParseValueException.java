package graphql.schema;

import graphql.GraphQLException;

public class CoercingParseValueException extends GraphQLException {

    public CoercingParseValueException() {
    }

    public CoercingParseValueException(String message) {
        super(message);
    }

    public CoercingParseValueException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoercingParseValueException(Throwable cause) {
        super(cause);
    }
}
