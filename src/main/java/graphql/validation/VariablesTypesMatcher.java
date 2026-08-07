package graphql.validation;


import graphql.Internal;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.NullValue;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.schema.SchemaInputType;
import graphql.schema.SchemaList;
import graphql.schema.SchemaNamedType;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaType;

@Internal
public class VariablesTypesMatcher {

    /**
     * This method and variable naming was inspired from the reference graphql-js implementation
     *
     * @param varType                 the variable type
     * @param varDefaultValue         the default value for the variable
     * @param locationType            the location type where the variable was encountered
     * @param hasLocationDefaultValue whether that location has a default value
     *
     * @return true if the variable matches ok
     */
    public boolean doesVariableTypesMatch(
            Type<?> varType,
            Value<?> varDefaultValue,
            SchemaInputType locationType,
            boolean hasLocationDefaultValue) {
        if (locationType instanceof SchemaNonNull
                && !(varType instanceof NonNullType)) {
            boolean hasNonNullVariableDefaultValue =
                    varDefaultValue != null && !(varDefaultValue instanceof NullValue);
            if (!hasNonNullVariableDefaultValue && !hasLocationDefaultValue) {
                return false;
            }
            SchemaType nullableLocationType =
                    ((SchemaNonNull) locationType).getWrappedType();
            return checkType(varType, nullableLocationType);
        }
        return checkType(varType, locationType);
    }


    public String effectiveTypeName(
            Type<?> variableType,
            Value<?> defaultValue) {
        if (defaultValue == null || defaultValue instanceof NullValue) {
            return simplePrint(variableType);
        }
        if (variableType instanceof NonNullType) {
            return simplePrint(variableType);
        }
        return simplePrint(variableType) + "!";
    }

    @SuppressWarnings("SimplifiableIfStatement")
    private boolean checkType(
            Type<?> actualType,
            SchemaType expectedType) {

        if (expectedType instanceof SchemaNonNull) {
            if (actualType instanceof NonNullType) {
                return checkType(
                        ((NonNullType) actualType).getType(),
                        ((SchemaNonNull) expectedType).getWrappedType());
            }
            return false;
        }

        if (actualType instanceof NonNullType) {
            return checkType(
                    ((NonNullType) actualType).getType(),
                    expectedType);
        }

        if (actualType instanceof ListType
                && expectedType instanceof SchemaList) {
            return checkType(
                    ((ListType) actualType).getType(),
                    ((SchemaList) expectedType).getWrappedType());
        }
        if (actualType instanceof ListType
                || expectedType instanceof SchemaList) {
            return false;
        }
        if (!(actualType instanceof TypeName)
                || !(expectedType instanceof SchemaNamedType)) {
            return false;
        }
        return ((TypeName) actualType).getName().equals(
                ((SchemaNamedType) expectedType).getName());
    }

    private String simplePrint(Type<?> type) {
        if (type instanceof NonNullType) {
            return simplePrint(((NonNullType) type).getType()) + "!";
        }
        if (type instanceof ListType) {
            return "[" + simplePrint(((ListType) type).getType()) + "]";
        }
        return ((TypeName) type).getName();
    }

}
