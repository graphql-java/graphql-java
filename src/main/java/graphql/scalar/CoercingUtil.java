package graphql.scalar;

import graphql.Internal;

@Internal
class CoercingUtil {
    static boolean isNumberIsh(Object input) {
        return input instanceof Number || input instanceof String;
    }

    static String typeName(Object input) {
        if (input == null) {
            return "null";
        }

        return input.getClass().getSimpleName();
    }
}
