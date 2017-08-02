package graphql.execution.batched;

import graphql.GraphQLException;



/**
 * Thrown when an exception occurred while trying to fetch data from a GraphQL field.
 */
public class DataFetchingException extends GraphQLException {
    public DataFetchingException() {
        super();
    }

    public DataFetchingException(String message) {
        super(message);
    }

    public DataFetchingException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataFetchingException(Throwable cause) {
        super(cause);
    }
}
