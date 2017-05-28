package graphql.execution;

import graphql.GraphQLException;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

/**
 * https://facebook.github.io/graphql/#sec-Input-Objects
 *
 *  - This unordered map should not contain any entries with names not defined by a field of this input object type, otherwise an error should be thrown.
 */
public class InputMapDefinesTooManyFieldsException extends GraphQLException {

    public InputMapDefinesTooManyFieldsException(GraphQLType graphQLType, String fieldName) {
        super(String.format("The variables input contains a field name '%s' that is not defined for input object type '%s' ", GraphQLTypeUtil.getUnwrappedTypeName(graphQLType), fieldName));
    }

}
