package graphql;


@PublicApi
public class AssertException extends GraphQLInstanceException {

    public AssertException(String message) {
        super(message);
    }
}
