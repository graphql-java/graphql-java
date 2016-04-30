package graphql;

import java.util.Collection;

public class Assert {


    public static void assertNotNull(Object object, String errorMessage) {
        if (object != null) return;
        throw new AssertException(errorMessage);
    }

    public static void assertNotEmpty(Collection<?> c, String errorMessage) {
        if (c == null || c.isEmpty()) throw new AssertException(errorMessage);
        return;
    }

}
