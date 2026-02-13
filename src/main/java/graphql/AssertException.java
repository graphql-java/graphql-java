package graphql;


import org.jspecify.annotations.NullMarked;

@PublicApi
@NullMarked
public class AssertException extends GraphQLException {

    public AssertException(String message) {
        super(message);
    }
}
