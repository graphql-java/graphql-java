package graphql;


public class ShouldNotHappenException extends GraphQLException {

    public ShouldNotHappenException() {
        super("This should not happen ..it's probably a bug");
    }
}
