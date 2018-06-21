package graphql.validation.rules;


import graphql.Internal;
import graphql.language.Value;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;

import static graphql.schema.GraphQLNonNull.*;

@Internal
public class VariablesTypesMatcher {

    public boolean doesVariableTypesMatch(GraphQLType variableType, Value variableDefaultValue, GraphQLType expectedType) {
        return checkType(effectiveType(variableType, variableDefaultValue), expectedType);
    }

    public GraphQLType effectiveType(GraphQLType variableType, Value defaultValue) {
        if (defaultValue == null) return variableType;
        if (variableType instanceof GraphQLNonNull) return variableType;
        return nonNull(variableType);
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
