package graphql;

import java.util.Collection;

public class Assert {


    public static <T> T assertNotNull(T object, String errorMessage) {
        if (object != null) return object;
        throw new AssertException(errorMessage);
    }

    public static <T> T assertNotNull(T object) {
        return assertNotNull(object,"Must be non null");
    }

    public static <T> Collection<T> assertNotEmpty(Collection<T> c, String errorMessage) {
        if (c == null || c.isEmpty()) throw new AssertException(errorMessage);
        return c;
    }

}
