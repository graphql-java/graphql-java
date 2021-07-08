package graphql.parser;

import graphql.Internal;

import java.io.IOException;
import java.io.StringWriter;

import static graphql.Assert.assertShouldNeverHappen;

/**
 * Contains Unicode helpers for parsing StringValue types in the grammar
 */
@Internal
public class UnicodeUtil {
    public static int MAX_UNICODE_CODE_POINT = 0x10FFFF;
    public static int LEADING_SURROGATE_LOWER_BOUND = 0xD800;
    public static int LEADING_SURROGATE_UPPER_BOUND = 0xDBFF;
    public static int TRAILING_SURROGATE_LOWER_BOUND = 0xDC00;
    public static int TRAILING_SURROGATE_UPPER_BOUND = 0xDFFF;

    public static int parseAndWriteUnicode(StringWriter writer, String string, int i) {
        // Unicode code points can either be:
        //  1. Unbraced: four hex characters in the form \\u597D, or
        //  2. Braced: any number of hex characters surrounded by braces in the form \\u{1F37A}

        // Extract the code point hex digits. Index i points to 'u'
        int startIndex = isBracedEscape(string, i) ? i + 2 : i + 1;
        int endIndexExclusive = getEndIndexExclusive(string, i);
        // Index for parser to continue at, the last character of the escaped unicode character. Either } or hex digit
        int continueIndex = isBracedEscape(string, i) ? endIndexExclusive : endIndexExclusive - 1;

        String hexStr = string.substring(startIndex, endIndexExclusive);
        Integer codePoint = Integer.parseInt(hexStr, 16);

        if (isTrailingSurrogateValue(codePoint)) {
            // A trailing surrogate value must be preceded with a leading surrogate value
            throw new RuntimeException("Invalid unicode");
        } else if (isLeadingSurrogateValue(codePoint)) {
            // A leading surrogate value must be followed by a trailing surrogate value
            if (!isEscapedUnicode(string, continueIndex + 1)) {
                throw new RuntimeException("Invalid unicode");
            }

            // Shift parser ahead to 'u' in second escaped Unicode character
            i = continueIndex + 2;
            int trailingStartIndex = isBracedEscape(string, i) ? i + 2 : i + 1;
            int trailingEndIndexExclusive = getEndIndexExclusive(string, i);
            String trailingHexStr = string.substring(trailingStartIndex, trailingEndIndexExclusive);
            Integer trailingCodePoint = Integer.parseInt(trailingHexStr, 16);

            if (isTrailingSurrogateValue(trailingCodePoint)) {
                writeCodePoint(writer, codePoint);
                writeCodePoint(writer, trailingCodePoint);
                continueIndex = isBracedEscape(string, i) ? trailingEndIndexExclusive : trailingEndIndexExclusive - 1;
                return continueIndex;
            }

            throw new RuntimeException("Invalid unicode");
        } else if (isValidUnicodeCodePoint(codePoint)) {
            writeCodePoint(writer, codePoint);
            return continueIndex;
        }

        throw new RuntimeException("Invalid unicode");
    }

    private static int getEndIndexExclusive(String string, int i) {
        // Unbraced case, with exactly 4 hex digits
        if (string.length() > i + 5 && !isBracedEscape(string, i)) {
            return i + 5;
        }

        // Braced case, with any number of hex digits
        int endIndexExclusive = i + 2;
        do {
            if (endIndexExclusive + 1 >= string.length()) {
                throw new RuntimeException("Invalid unicode");
            }
        } while (string.charAt(++endIndexExclusive) != '}');

        return endIndexExclusive;
    }

    private static boolean isValidUnicodeCodePoint(int value) {
        return value <= MAX_UNICODE_CODE_POINT;
    }

    private static boolean isEscapedUnicode(String string, int index) {
        if (index + 1 >= string.length()) {
            throw new RuntimeException("Invalid unicode");
        }
        return string.charAt(index) == '\\' && string.charAt(index + 1) == 'u';
    }

    private static boolean isLeadingSurrogateValue(int value) {
        return LEADING_SURROGATE_LOWER_BOUND <= value && value <= LEADING_SURROGATE_UPPER_BOUND;
    }

    private static boolean isTrailingSurrogateValue(int value) {
        return TRAILING_SURROGATE_LOWER_BOUND <= value && value <= TRAILING_SURROGATE_UPPER_BOUND;
    }

    private static void writeCodePoint(StringWriter writer, int codepoint) {
        char[] chars = Character.toChars(codepoint);
        try {
            writer.write(chars);
        } catch (IOException e) {
            assertShouldNeverHappen();
        }
    }

    private static boolean isBracedEscape(String string, int i) {
        return string.charAt(i + 1) == '{';
    }
}
