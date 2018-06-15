package graphql;


@PublicApi
public class AssertException extends GraphQLSchemaException {

    public AssertException(String message) {
        super(message);
    }
}
