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

    public UnresolvedTypeException(GraphQLOutputType interfaceOrUnionType) {
        super("Could not determine the exact type of '" + interfaceOrUnionType.getName() + "'");
        this.interfaceOrUnionType = interfaceOrUnionType;
    }

    public GraphQLOutputType getInterfaceOrUnionType() {
        return interfaceOrUnionType;
    }
}
