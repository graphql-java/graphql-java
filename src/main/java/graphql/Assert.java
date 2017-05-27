package graphql;

import java.util.Collection;

@Internal
public class Assert {

    public static <T> T assertNotNull(T object, String errorMessage) {
        if (object != null) return object;
        throw new AssertException(errorMessage);
    }

    public static <T> T assertNeverCalled() {
        throw new AssertException("Should never been called");
    }

    public static <T> T assertShouldNeverHappen(String errorMessage) {
        throw new AssertException("Internal error: should never happen: " + errorMessage);
    }

    public static <T> T assertShouldNeverHappen() {
        throw new AssertException("Internal error: should never happen");
    }

    public static <T> Collection<T> assertNotEmpty(Collection<T> c, String errorMessage) {
        if (c == null || c.isEmpty()) throw new AssertException(errorMessage);
        return c;
    }

    public static void assertTrue(boolean condition, String errorMessage) {
        if (condition) return;
        throw new AssertException(errorMessage);
    }

    private static final String invalidNameErrorMessage = "Name must be non-null, non-empty and match [_A-Za-z][_0-9A-Za-z]*";

    /**
     * Validates that the Lexical token name matches the current spec.
     * currently non null, non empty,
     *
     * @param name - the name to be validated.
     * @return the name if valid, or AssertException if invalid.
     */
    public static String assertValidName(String name) {
        if (name != null && !name.isEmpty() && name.matches("[_A-Za-z][_0-9A-Za-z]*")) {
            return name;
        }
        throw new AssertException(invalidNameErrorMessage);
    }

}
