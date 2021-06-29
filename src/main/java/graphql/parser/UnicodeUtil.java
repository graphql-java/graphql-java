package graphql.parser;

import graphql.Assert;
import graphql.Internal;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Contains Unicode helpers for parsing StringValue types in the grammar
 */
@Internal
public class UnicodeUtil {
    public static int MAX_UNICODE_CODE_POINT = 0x10FFFF;

    public static int parseAndWriteUnicode(StringWriter writer, String string, int i) {
        // Unicode characters can either be:
        //  - four hex characters in the form \\u597D, or
        //  - any number of hex characters surrounded by a brace in the form \\u{1F37A}

        // Four hex character only case \\u597D, for code points in the Basic Multilingual Plane (BMP)
        if (isNotBracedEscape(string, i)) {
            String hexStr = string.substring(i + 1, i + 5);
            int codepoint = Integer.parseInt(hexStr, 16);
            writer.write(codepoint);
            return i + 4;
            // TODO error checking of invalid values
        } else {
            // Any number of hex characters e.g. \\u{1F37A}, which allows code points outside the Basic Multilingual Plane (BMP)
            int startIx = i + 2;
            int endIndexExclusive = startIx;
            do {
                if (endIndexExclusive + 1 >= string.length()) {
                    throw new RuntimeException("invalid unicode encoding");
                }
            } while (string.charAt(++endIndexExclusive) != '}');

            String hexStr = string.substring(startIx, endIndexExclusive);
            Integer hexValue = Integer.parseInt(hexStr, 16);
            if (isValidUnicodeCodePoint(hexValue)) {
                char[] chars = Character.toChars(hexValue);
                try {
                    writer.write(chars);
                } catch (IOException e) {
                    return Assert.assertShouldNeverHappen();
                }
                return endIndexExclusive;
            } else {
                throw new RuntimeException("invalid unicode code point");
            }
        }
//        Assert.assertShouldNeverHappen();
        // TODO error checking of invalid values
    }

    private static boolean isNotBracedEscape(String string, int i) {
        return string.charAt(i + 1) != '{';
    }

    private static boolean isValidUnicodeCodePoint(Integer value) {
        // TODO: Add bad surrogate checks
        return value <= MAX_UNICODE_CODE_POINT;
    }
}
