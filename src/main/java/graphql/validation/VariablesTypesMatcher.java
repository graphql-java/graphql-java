package graphql.validation;


import graphql.Internal;
import graphql.language.NullValue;
import graphql.language.Value;
import graphql.schema.GraphQLType;

import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;

@Internal
public class VariablesTypesMatcher {

    /**
     * This method and variable naming was inspired from the reference graphql-js implementation
     *
     * @param varType              the variable type
     * @param varDefaultValue      the default value for the variable
     * @param locationType         the location type where the variable was encountered
     * @param locationDefaultValue the default value for that location
     *
     * @return true if the variable matches ok
     */
    public boolean doesVariableTypesMatch(GraphQLType varType, Value<?> varDefaultValue, GraphQLType locationType, Value<?> locationDefaultValue) {
        if (isNonNull(locationType) && !isNonNull(varType)) {
            boolean hasNonNullVariableDefaultValue =
                    varDefaultValue != null && !(varDefaultValue instanceof NullValue);
            boolean hasLocationDefaultValue = locationDefaultValue != null;
            if (!hasNonNullVariableDefaultValue && !hasLocationDefaultValue) {
                return false;
            }
            GraphQLType nullableLocationType = unwrapNonNull(locationType);
            return checkType(varType, nullableLocationType);
        }
        return checkType(varType, locationType);
    }


    public GraphQLType effectiveType(GraphQLType variableType, Value<?> defaultValue) {
        if (defaultValue == null || defaultValue instanceof NullValue) {
            return variableType;
        }
        if (isNonNull(variableType)) {
            return variableType;
        }
        return nonNull(variableType);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean checkType(GraphQLType actualType, GraphQLType expectedType) {

        if (isNonNull(expectedType)) {
            if (isNonNull(actualType)) {
                return checkType(unwrapOne(actualType), unwrapOne(expectedType));
            }
            return false;
        }

        if (isNonNull(actualType)) {
            return checkType(unwrapOne(actualType), expectedType);
        }


        if (isList(actualType) && isList(expectedType)) {
            return checkType(unwrapOne(actualType), unwrapOne(expectedType));
        }
        return actualType == expectedType;
    }

}
