package graphql;


@PublicApi
public class AssertInternalException extends GraphQLException {

    public AssertInternalException(String message) {
        super(message);
    }
}
