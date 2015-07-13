package graphql.validation;


import graphql.language.Value;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;

public class ValidationUtil {

    public static boolean isValidLiteralValue(Value value, GraphQLType type) {
        if (value == null) {
            return !(type instanceof GraphQLNonNull);
        }
        return true;
    }
}
