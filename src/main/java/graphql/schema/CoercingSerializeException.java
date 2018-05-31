package graphql.schema;

import graphql.GraphQLInstanceException;
import graphql.PublicApi;

@PublicApi
public class CoercingSerializeException extends GraphQLInstanceException {

    public CoercingSerializeException() {
    }

    public CoercingSerializeException(String message) {
        super(message);
    }

    public CoercingSerializeException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoercingSerializeException(Throwable cause) {
        super(cause);
    }
}
