package graphql.execution;

import graphql.GraphQLException;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import static java.lang.String.format;

/**
 * This is thrown if a non nullable value is coerced to a null value
 */
public class NonNullableValueCoercedAsNullException extends GraphQLException {

    public NonNullableValueCoercedAsNullException(VariableDefinition variableDefinition, GraphQLType graphQLType) {
        super(format("Variable '%s' has coerced Null value for NonNull type '%s'",
                variableDefinition.getName(), GraphQLTypeUtil.getUnwrappedTypeName(graphQLType)));
    }

    public NonNullableValueCoercedAsNullException(GraphQLInputObjectField inputTypeField) {
        super(format("Input field '%s' has coerced Null value for NonNull type '%s'",
                inputTypeField.getName(), GraphQLTypeUtil.getUnwrappedTypeName(inputTypeField.getType())));
    }

}
