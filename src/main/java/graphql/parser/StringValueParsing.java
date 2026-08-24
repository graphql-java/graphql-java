package graphql.parser;

import graphql.Assert;
import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;

import java.io.StringWriter;

/**
 * Contains parsing code for the StringValue types in the grammar
 */
@Internal
public class StringValueParsing {
    private final static String ESCAPED_TRIPLE_QUOTES = "\\\\\"\"\""; // ahh Java + Regex
    private final static String THREE_QUOTES = "\"\"\"";

    public static String parseTripleQuotedString(String strText) {
        int end = strText.length() - 3;
        String s = strText.substring(3, end);
        s = s.replaceAll(ESCAPED_TRIPLE_QUOTES, THREE_QUOTES);
        return removeIndentation(s);
    }

    /*
       See https://github.com/facebook/graphql/pull/327/files#diff-fe406b08746616e2f5f00909488cce66R758
     */
    public static String removeIndentation(String rawValue) {
        String[] lines = rawValue.split("\\n");
        Integer commonIndent = null;
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                continue;
            }
            String line = lines[i];
            int length = line.length();
            int indent = leadingWhitespace(line);
            if (indent < length) {
                if (commonIndent == null || indent < commonIndent) {
                    commonIndent = indent;
                }
            }
        }
        int firstLine = 0;
        while (firstLine < lines.length && containsOnlyWhiteSpace(lines[firstLine])) {
            firstLine++;
        }
        int lastLine = lines.length;
        while (lastLine > firstLine && containsOnlyWhiteSpace(lines[lastLine - 1])) {
            lastLine--;
        }

        StringBuilder formatted = new StringBuilder(rawValue.length());
        for (int i = firstLine; i < lastLine; i++) {
            String line = lines[i];
            if (commonIndent != null && i > 0 && line.length() > commonIndent) {
                line = line.substring(commonIndent);
            }
            if (i > firstLine) {
                formatted.append('\n');
            }
            formatted.append(line);
        }
        return formatted.toString();
    }

    private static int leadingWhitespace(String str) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch != ' ' && ch != '\t') {
                break;
            }
            count++;
        }
        return count;
    }

    private static boolean containsOnlyWhiteSpace(String str) {
        // according to graphql spec and graphql-js - this is the definition
        return leadingWhitespace(str) == str.length();
    }

    public static String parseSingleQuotedString(I18n i18n, String string, SourceLocation sourceLocation) {
        StringWriter writer = new StringWriter(string.length() - 2);
        int end = string.length() - 1;
        for (int i = 1; i < end; i++) {
            char c = string.charAt(i);
            if (c != '\\') {
                writer.write(c);
                continue;
            }
            char escaped = string.charAt(i + 1);
            i += 1;
            switch (escaped) {
                case '"':
                    writer.write('"');
                    continue;
                case '/':
                    writer.write('/');
                    continue;
                case '\\':
                    writer.write('\\');
                    continue;
                case 'b':
                    writer.write('\b');
                    continue;
                case 'f':
                    writer.write('\f');
                    continue;
                case 'n':
                    writer.write('\n');
                    continue;
                case 'r':
                    writer.write('\r');
                    continue;
                case 't':
                    writer.write('\t');
                    continue;
                case 'u':
                    i = UnicodeUtil.parseAndWriteUnicode(i18n, writer, string, i, sourceLocation);
                    continue;
                default:
                    Assert.assertShouldNeverHappen();
            }
        }
        return writer.toString();
    }
}
