package graphql;

/**
 * Represents customer issues with the query:
 *
 *   - operation, variables (request validation)
 *   - syntax (query validation)
 *   - schema dissonance (query is valid but doesn't match schema)
 *   - scalar coercion parsing
 */
public abstract class GraphQLQueryException extends GraphQLException {
    public GraphQLQueryException() {
    }

    public GraphQLQueryException(String message) {
        super(message);
    }

    public GraphQLQueryException(String message, Throwable cause) {
        super(message, cause);
    }

    public GraphQLQueryException(Throwable cause) {
        super(cause);
    }
}
