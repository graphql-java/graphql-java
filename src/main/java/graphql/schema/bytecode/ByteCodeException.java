package graphql.schema.bytecode;

import graphql.GraphQLException;

public class ByteCodeException extends GraphQLException {
    public ByteCodeException(String message) {
        super(message);
    }

    public ByteCodeException(String message, Throwable cause) {
        super(message, cause);
    }


}
