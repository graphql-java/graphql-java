package graphql.parser;

import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.SourceLocation;
import graphql.parser.exceptions.InvalidUnicodeSyntaxException;

import java.io.IOException;
import java.io.StringWriter;

import static graphql.Assert.assertShouldNeverHappen;

/**
 * Contains Unicode helpers for parsing StringValue types in the grammar
 */
@Internal
public class UnicodeUtil {
    public static final int MAX_UNICODE_CODE_POINT = 0x10FFFF;
    public static final int LEADING_SURROGATE_LOWER_BOUND = 0xD800;
    public static final int LEADING_SURROGATE_UPPER_BOUND = 0xDBFF;
    public static final int TRAILING_SURROGATE_LOWER_BOUND = 0xDC00;
    public static final int TRAILING_SURROGATE_UPPER_BOUND = 0xDFFF;

    public static int parseAndWriteUnicode(I18n i18n, StringWriter writer, String string, int i, SourceLocation sourceLocation) {
        // Unicode code points can either be:
        //  1. Unbraced: four hex characters in the form \\u597D, or
        //  2. Braced: any number of hex characters surrounded by braces in the form \\u{1F37A}

        // Extract the code point hex digits. Index i points to 'u'
        int startIndex = isBracedEscape(string, i) ? i + 2 : i + 1;
        int endIndexExclusive = getEndIndexExclusive(i18n, string, i, sourceLocation);
        // Index for parser to continue at, the last character of the escaped unicode character. Either } or hex digit
        int continueIndex = isBracedEscape(string, i) ? endIndexExclusive : endIndexExclusive - 1;

        String hexStr = string.substring(startIndex, endIndexExclusive);
        int codePoint;
        try {
            codePoint = Integer.parseInt(hexStr, 16);
        } catch (NumberFormatException e) {
            throw new InvalidUnicodeSyntaxException(i18n, "InvalidUnicode.invalidHexString", sourceLocation, offendingToken(string, i, continueIndex));
        }

        if (isTrailingSurrogateValue(codePoint)) {
            throw new InvalidUnicodeSyntaxException(i18n, "InvalidUnicode.trailingLeadingSurrogate", sourceLocation, offendingToken(string, i, continueIndex));
        } else if (isLeadingSurrogateValue(codePoint)) {
            if (!isEscapedUnicode(string, continueIndex + 1)) {
                throw new InvalidUnicodeSyntaxException(i18n, "InvalidUnicode.leadingTrailingSurrogate", sourceLocation, offendingToken(string, i, continueIndex));
            }

            // Shift parser ahead to 'u' in second escaped Unicode character
            i = continueIndex + 2;
            int trailingStartIndex = isBracedEscape(string, i) ? i + 2 : i + 1;
            int trailingEndIndexExclusive = getEndIndexExclusive(i18n, string, i, sourceLocation);
            String trailingHexStr = string.substring(trailingStartIndex, trailingEndIndexExclusive);
            int trailingCodePoint = Integer.parseInt(trailingHexStr, 16);
            continueIndex = isBracedEscape(string, i) ? trailingEndIndexExclusive : trailingEndIndexExclusive - 1;

            if (isTrailingSurrogateValue(trailingCodePoint)) {
                writeCodePoint(writer, codePoint);
                writeCodePoint(writer, trailingCodePoint);
                return continueIndex;
            }

            throw new InvalidUnicodeSyntaxException(i18n, "InvalidUnicode.leadingTrailingSurrogate", sourceLocation, offendingToken(string, i, continueIndex));
        } else if (isValidUnicodeCodePoint(codePoint)) {
            writeCodePoint(writer, codePoint);
            return continueIndex;
        }

        throw new InvalidUnicodeSyntaxException(i18n, "InvalidUnicode.invalidCodePoint", sourceLocation, offendingToken(string, i, continueIndex));
    }

    private static String offendingToken(String string, int i, int continueIndex) {
        return string.substring(i - 1, continueIndex + 1);
    }

    private static int getEndIndexExclusive(I18n i18n, String string, int i, SourceLocation sourceLocation) {
        // Unbraced case, with exactly 4 hex digits
        if (string.length() > i + 5 && !isBracedEscape(string, i)) {
            return i + 5;
        }

        // Braced case, with any number of hex digits
        int endIndexExclusive = i + 2;
        do {
            if (endIndexExclusive + 1 >= string.length()) {
                throw new InvalidUnicodeSyntaxException(i18n, "InvalidUnicode.incorrectEscape", sourceLocation, string.substring(i - 1, endIndexExclusive));
            }
        } while (string.charAt(++endIndexExclusive) != '}');

        return endIndexExclusive;
    }

    private static boolean isValidUnicodeCodePoint(int value) {
        return value <= MAX_UNICODE_CODE_POINT;
    }

    private static boolean isEscapedUnicode(String string, int index) {
        if (index + 1 >= string.length()) {
            return false;
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
