package graphql;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.lang.String.format;

@SuppressWarnings("TypeParameterUnusedInFormals")
@Internal
public class Assert {

    public static <T> T assertNotNull(T object, Supplier<String> msg) {
        if (object != null) {
            return object;
        }
        throw new AssertException(msg.get());
    }

    public static <T> T assertNotNullWithNPE(T object, Supplier<String> msg) {
        if (object != null) {
            return object;
        }
        throw new NullPointerException(msg.get());
    }

    public static <T> T assertNotNull(T object) {
        if (object != null) {
            return object;
        }
        throw new AssertException("Object required to be not null");
    }

    public static <T> void assertNull(T object, Supplier<String> msg) {
        if (object == null) {
            return;
        }
        throw new AssertException(msg.get());
    }

    public static <T> void assertNull(T object) {
        if (object == null) {
            return;
        }
        throw new AssertException("Object required to be null");
    }

    public static <T> T assertNeverCalled() {
        throw new AssertException("Should never been called");
    }

    public static <T> T assertShouldNeverHappen(String format, Object... args) {
        throw new AssertException("Internal error: should never happen: " + format(format, args));
    }

    public static <T> T assertShouldNeverHappen() {
        throw new AssertException("Internal error: should never happen");
    }

    public static <T> Collection<T> assertNotEmpty(Collection<T> collection) {
        if (collection == null || collection.isEmpty()) {
            throw new AssertException("collection must be not null and not empty");
        }
        return collection;
    }

    public static <T> Collection<T> assertNotEmpty(Collection<T> collection, Supplier<String> msg) {
        if (collection == null || collection.isEmpty()) {
            throw new AssertException(msg.get());
        }
        return collection;
    }

    public static void assertTrue(boolean condition, Supplier<String> msg) {
        if (condition) {
            return;
        }
        throw new AssertException(msg.get());
    }

    public static void assertTrue(boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertException("condition expected to be true");
    }

    public static void assertFalse(boolean condition, Supplier<String> msg) {
        if (!condition) {
            return;
        }
        throw new AssertException(msg.get());
    }

    public static void assertFalse(boolean condition) {
        if (!condition) {
            return;
        }
        throw new AssertException("condition expected to be false");
    }

    private static final String invalidNameErrorMessage = "Name must be non-null, non-empty and match [_A-Za-z][_0-9A-Za-z]* - was '%s'";

    private static final Pattern validNamePattern = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*");

    /**
     * Validates that the Lexical token name matches the current spec.
     * currently non null, non empty,
     *
     * @param name - the name to be validated.
     * @return the name if valid, or AssertException if invalid.
     */
    public static String assertValidName(String name) {
        if (name != null && !name.isEmpty() && validNamePattern.matcher(name).matches()) {
            return name;
        }
        throw new AssertException(String.format(invalidNameErrorMessage, name));
    }

}
