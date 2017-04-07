package graphql.relay;

import java.nio.charset.Charset;

/**
 * @deprecated use {@link java.util.Base64} instead.
 */
@Deprecated
public class Base64 {

    private Base64() {
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
    private static final java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();

    public static String toBase64(String string) {
        return encoder.encodeToString(string.getBytes(UTF8));
    }

    public static String fromBase64(String string) {
        return new String(decoder.decode(string), UTF8);
    }
}
