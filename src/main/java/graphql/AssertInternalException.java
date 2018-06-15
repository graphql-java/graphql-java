package graphql;

/**
 * This exception is raised when there
 * is an internal error with the library.
 *
 * e.g:
 * - unsupported feature
 * - wrong use of the library
 *
 */
@PublicApi
public class AssertInternalException extends GraphQLException {

    public AssertInternalException(String message) {
        super(message);
    }
}
