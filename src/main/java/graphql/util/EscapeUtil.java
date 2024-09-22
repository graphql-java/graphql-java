package graphql.util;

import graphql.Internal;

@Internal
public final class EscapeUtil {

    private EscapeUtil() {
    }

    /**
     * Encodes the value as a JSON string according to <a href="https://json.org/">https://json.org/</a> rules
     *
     * @param stringValue the value to encode as a JSON string
     *
     * @return the encoded string
     */
    public static String escapeJsonString(String stringValue) {
        StringBuilder sb = new StringBuilder(stringValue.length());
        escapeJsonStringTo(sb, stringValue);
        return sb.toString();
    }

    public static void escapeJsonStringTo(StringBuilder output, String stringValue) {
        int len = stringValue.length();
        for (int i = 0; i < len; i++) {
            char ch = stringValue.charAt(i);
            switch (ch) {
                case '"':
                    output.append("\\\"");
                    break;
                case '\\':
                    output.append("\\\\");
                    break;
                case '\b':
                    output.append("\\b");
                    break;
                case '\f':
                    output.append("\\f");
                    break;
                case '\n':
                    output.append("\\n");
                    break;
                case '\r':
                    output.append("\\r");
                    break;
                case '\t':
                    output.append("\\t");
                    break;
                default:
                    output.append(ch);
            }
        }
    }

}
