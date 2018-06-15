package graphql;

/**
 * Represents customer issues with:
 *
 *   - data fetching (resolver errors)
 *   - data completion (type resolution, scalar coercion serialization)
 */
public abstract class GraphQLExecutionException extends GraphQLException {
    public GraphQLExecutionException() {
    }

    public GraphQLExecutionException(String message) {
        super(message);
    }

    public GraphQLExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public GraphQLExecutionException(Throwable cause) {
        super(cause);
    }
}
