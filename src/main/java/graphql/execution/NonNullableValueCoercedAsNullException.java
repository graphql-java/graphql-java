package graphql.execution;

import graphql.GraphQLException;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

/**
 * This is thrown if a non nullable value is coerced to a null value
 */
public class NonNullableValueCoercedAsNullException extends GraphQLException {

    public NonNullableValueCoercedAsNullException(GraphQLType graphQLType) {
        super(String.format("Null value for NonNull type '%s", GraphQLTypeUtil.getUnwrappedTypeName(graphQLType)));
    }

}
