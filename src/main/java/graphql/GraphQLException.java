package graphql;


/**
 * <p>GraphQLException class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLException extends RuntimeException{

    /**
     * <p>Constructor for GraphQLException.</p>
     */
    public GraphQLException() {
    }

    /**
     * <p>Constructor for GraphQLException.</p>
     *
     * @param message a {@link java.lang.String} object.
     */
    public GraphQLException(String message) {
        super(message);
    }

    /**
     * <p>Constructor for GraphQLException.</p>
     *
     * @param message a {@link java.lang.String} object.
     * @param cause a {@link java.lang.Throwable} object.
     */
    public GraphQLException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * <p>Constructor for GraphQLException.</p>
     *
     * @param cause a {@link java.lang.Throwable} object.
     */
    public GraphQLException(Throwable cause) {
        super(cause);
    }


}
