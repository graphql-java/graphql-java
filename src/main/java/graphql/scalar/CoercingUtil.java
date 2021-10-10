package graphql.scalar;

import graphql.Internal;

import java.util.regex.Pattern;

@Internal
class CoercingUtil {

    static Pattern validNumberPattern = Pattern.compile("-?\\d+(\\.\\d+)?");

    static boolean isNumberIsh(Object input) {
        if (input instanceof Number) {
            return true;
        }

        if (input instanceof String) {
            String stringValue = (String) input;
            return validNumberPattern.matcher(stringValue).matches();
        }

        return false;
    }

    static String typeName(Object input) {
        if (input == null) {
            return "null";
        }

        return input.getClass().getSimpleName();
    }
}
