package graphql.execution;

import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.schema.GraphQLOutputType;

/**
 * This is thrown if a {@link graphql.schema.TypeResolver} fails to give back a concrete type
 * for an interface or union type at runtime.
 */
@PublicApi
public class UnresolvedTypeException extends GraphQLException {

    private final GraphQLOutputType interfaceOrUnionType;

    /**
     * Constructor to use a custom error message
     * for an error that happened during type resolution.
     *
     * @param message              custom error message.
     * @param interfaceOrUnionType expected type.
     */
    public UnresolvedTypeException(String message, GraphQLOutputType interfaceOrUnionType) {
        super(message);
        this.interfaceOrUnionType = interfaceOrUnionType;
    }

    public UnresolvedTypeException(GraphQLOutputType interfaceOrUnionType) {
        this("Could not determine the exact type of '" + interfaceOrUnionType.getName() + "'", interfaceOrUnionType);
    }

    public GraphQLOutputType getInterfaceOrUnionType() {
        return interfaceOrUnionType;
    }

}
