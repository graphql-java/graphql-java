package graphql;


/**
 * <p>Assert class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class Assert {


    /**
     * <p>assertNotNull.</p>
     *
     * @param object a {@link java.lang.Object} object.
     * @param errorMessage a {@link java.lang.String} object.
     */
    public static void assertNotNull(Object object, String errorMessage) {
        if (object != null) return;
        throw new AssertException(errorMessage);
    }

}
