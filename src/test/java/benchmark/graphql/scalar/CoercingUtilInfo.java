package benchmark.graphql.scalar;

import java.util.regex.Pattern;

public class CoercingUtilInfo {

    static boolean isNumberIsh1(Object input) {
        return (input instanceof Number || input instanceof String);
    }

    static Pattern validNumberPattern = Pattern.compile("-?\\d+(\\.\\d+)?");

    static boolean isNumberIsh2(Object input) {
        if (input instanceof Number) {
            return true;
        }

        if (input instanceof String) {
            String stringValue = (String) input;
            return validNumberPattern.matcher(stringValue).matches();
        }

        return false;
    }

}
