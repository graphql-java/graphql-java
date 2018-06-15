package graphql;

/**
 * Represents customer issues with:
 *
 *   - building the GraphQL Schema (validation)
 *   - building the GraphQL instance (validation)
 */
public abstract class GraphQLSchemaException extends GraphQLException {
    public GraphQLSchemaException() {
    }

    public GraphQLSchemaException(String message) {
        super(message);
    }

    public GraphQLSchemaException(String message, Throwable cause) {
        super(message, cause);
    }

    public GraphQLSchemaException(Throwable cause) {
        super(cause);
    }
}
