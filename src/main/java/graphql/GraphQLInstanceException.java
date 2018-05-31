package graphql;

/**
 * Represents customer issues with:
 *
 *   - building the GraphQL Schema (validation)
 *   - building the GraphQL instance (validation)
 *   - data fetching (resolver errors)
 *   - data completion (type resolution, scalar coercion serialization)
 */
public abstract class GraphQLInstanceException extends GraphQLException {
    public GraphQLInstanceException() {
    }

    public GraphQLInstanceException(String message) {
        super(message);
    }

    public GraphQLInstanceException(String message, Throwable cause) {
        super(message, cause);
    }

    public GraphQLInstanceException(Throwable cause) {
        super(cause);
    }
}
