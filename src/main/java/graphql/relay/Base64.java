package graphql.relay;

import java.nio.charset.Charset;
import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;


public class Base64 {

    private Base64() {
    }

    public static String toBase64(String string) {
        try {
            return DatatypeConverter.printBase64Binary(string.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String fromBase64(String string) {
        return new String(DatatypeConverter.parseBase64Binary(string), Charset.forName("UTF-8"));
    }
}
