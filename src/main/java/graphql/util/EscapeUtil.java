package graphql.util;

import graphql.Internal;

@Internal
public final class EscapeUtil {

    private EscapeUtil() {
    }

    /**
     * Encodes the value as a JSON string according to http://json.org/ rules
     *
     * @param stringValue the value to encode as a JSON string
     *
     * @return the encoded string
     */
    public static String escapeJsonString(String stringValue) {
        int len = stringValue.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char ch = stringValue.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

}
