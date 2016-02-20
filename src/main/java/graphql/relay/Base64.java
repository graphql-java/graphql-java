package graphql.relay;

import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;


/**
 * <p>Base64 class.</p>
 *
 * @author Andreas Marek
 * @version v1.3
 */
public class Base64 {

    /**
     * <p>toBase64.</p>
     *
     * @param string a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static String toBase64(String string) {
        try {
            return DatatypeConverter.printBase64Binary(string.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>fromBase64.</p>
     *
     * @param string a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public static String fromBase64(String string) {
        return new String(DatatypeConverter.parseBase64Binary(string));
    }
}
