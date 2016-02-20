package graphql;


/**
 * <p>ShouldNotHappenException class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class ShouldNotHappenException extends GraphQLException {

    /**
     * <p>Constructor for ShouldNotHappenException.</p>
     */
    public ShouldNotHappenException() {
        super("This should not happen ..it's probably a bug");
    }
}
