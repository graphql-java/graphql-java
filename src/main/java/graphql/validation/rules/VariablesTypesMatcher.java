package graphql.validation.rules;


import graphql.language.Value;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;

/**
 * <p>VariablesTypesMatcher class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class VariablesTypesMatcher {

    /**
     * <p>doesVariableTypesMatch.</p>
     *
     * @param variableType a {@link graphql.schema.GraphQLType} object.
     * @param variableDefaultValue a {@link graphql.language.Value} object.
     * @param expectedType a {@link graphql.schema.GraphQLType} object.
     * @return a boolean.
     */
    public boolean doesVariableTypesMatch(GraphQLType variableType, Value variableDefaultValue, GraphQLType expectedType) {
        return checkType(effectiveType(variableType, variableDefaultValue), expectedType);
    }

    private GraphQLType effectiveType(GraphQLType variableType, Value defaultValue) {
        if (defaultValue == null) return variableType;
        if (variableType instanceof GraphQLNonNull) return variableType;
        return new GraphQLNonNull(variableType);
    }

    private boolean checkType(GraphQLType actualType, GraphQLType expectedType) {

        if (expectedType instanceof GraphQLNonNull) {
            if (actualType instanceof GraphQLNonNull) {
                return checkType(((GraphQLNonNull) actualType).getWrappedType(), ((GraphQLNonNull) expectedType).getWrappedType());
            }
            return false;
        }

        if (actualType instanceof GraphQLNonNull) {
            return checkType(((GraphQLNonNull) actualType).getWrappedType(), expectedType);
        }


        if ((actualType instanceof GraphQLList) && (expectedType instanceof GraphQLList)) {
            return checkType(((GraphQLList) actualType).getWrappedType(), ((GraphQLList) expectedType).getWrappedType());
        }
        return actualType == expectedType;
    }

}
