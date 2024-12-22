package graphql.util;

import java.util.Locale;

public class StringKit {

    public static String capitalize(String s) {
        if (s != null && !s.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            // see https://github.com/graphql-java/graphql-java/issues/3385
            sb.append(s.substring(0, 1).toUpperCase(Locale.ROOT));
            if (s.length() > 1) {
                sb.append(s.substring(1));
            }
            return sb.toString();
        }
        return s;
    }

}
