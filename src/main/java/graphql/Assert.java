package graphql;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.lang.String.format;

@SuppressWarnings("TypeParameterUnusedInFormals")
@Internal
public class Assert {

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
        return throwAssert("Object required to be not null");
    }

    public static <T> T assertNotNull(T object, Supplier<String> msg) {
        if (object != null) {
            return object;
        }
        return throwAssert(msg.get());
    }

    public static <T> T assertNotNull(T object, String constantMsg) {
        if (object != null) {
            return object;
        }
        return throwAssert(constantMsg);
    }

    public static <T> T assertNotNull(T object, String msgFmt, Object arg1) {
        if (object != null) {
            return object;
        }
        return throwAssert(msgFmt, arg1);
    }

    public static <T> T assertNotNull(T object, String msgFmt, Object arg1, Object arg2) {
        if (object != null) {
            return object;
        }
        return throwAssert(msgFmt, arg1, arg2);
    }

    public static <T> T assertNotNull(T object, String msgFmt, Object arg1, Object arg2, Object arg3) {
        if (object != null) {
            return object;
        }
        return throwAssert(msgFmt, arg1, arg2, arg3);
    }


    public static <T> void assertNull(T object, Supplier<String> msg) {
        if (object == null) {
            return;
        }
        throwAssert(msg.get());
    }

    public static <T> void assertNull(T object) {
        if (object == null) {
            return;
        }
        throwAssert("Object required to be null");
    }

    public static <T> T assertNeverCalled() {
        return throwAssert("Should never been called");
    }

    public static <T> T assertShouldNeverHappen(String format, Object... args) {
        return throwAssert("Internal error: should never happen: %s", format(format, args));
    }

    public static <T> T assertShouldNeverHappen() {
        return throwAssert("Internal error: should never happen");
    }

    public static <T> Collection<T> assertNotEmpty(Collection<T> collection) {
        if (collection == null || collection.isEmpty()) {
            throwAssert("collection must be not null and not empty");
        }
        return collection;
    }

    public static <T> Collection<T> assertNotEmpty(Collection<T> collection, Supplier<String> msg) {
        if (collection == null || collection.isEmpty()) {
            throwAssert(msg.get());
        }
        return collection;
    }

    public static void assertTrue(boolean condition, Supplier<String> msg) {
        if (condition) {
            return;
        }
        throwAssert(msg.get());
    }

    public static void assertTrue(boolean condition) {
        if (condition) {
            return;
        }
        throwAssert("condition expected to be true");
    }

    public static void assertTrue(boolean condition, String constantMsg) {
        if (condition) {
            return;
        }
        throwAssert(constantMsg);
    }

    public static void assertTrue(boolean condition, String msgFmt, Object arg1) {
        if (condition) {
            return;
        }
        throwAssert(msgFmt, arg1);
    }

    public static void assertTrue(boolean condition, String msgFmt, Object arg1, Object arg2) {
        if (condition) {
            return;
        }
        throwAssert(msgFmt, arg1, arg2);
    }

    public static void assertTrue(boolean condition, String msgFmt, Object arg1, Object arg2, Object arg3) {
        if (condition) {
            return;
        }
        throwAssert(msgFmt, arg1, arg2, arg3);
    }

    public static void assertFalse(boolean condition, Supplier<String> msg) {
        if (!condition) {
            return;
        }
        throwAssert(msg.get());
    }

    public static void assertFalse(boolean condition) {
        if (!condition) {
            return;
        }
        throwAssert("condition expected to be false");
    }

    public static void assertFalse(boolean condition, String constantMsg) {
        if (!condition) {
            return;
        }
        throwAssert(constantMsg);
    }

    public static void assertFalse(boolean condition, String msgFmt, Object arg1) {
        if (!condition) {
            return;
        }
        throwAssert(msgFmt, arg1);
    }

    public static void assertFalse(boolean condition, String msgFmt, Object arg1, Object arg2) {
        if (!condition) {
            return;
        }
        throwAssert(msgFmt, arg1, arg2);
    }

    public static void assertFalse(boolean condition, String msgFmt, Object arg1, Object arg2, Object arg3) {
        if (!condition) {
            return;
        }
        throwAssert(msgFmt, arg1, arg2, arg3);
    }

    private static final String invalidNameErrorMessage = "Name must be non-null, non-empty and match [_A-Za-z][_0-9A-Za-z]* - was '%s'";

    private static final Pattern validNamePattern = Pattern.compile("[_A-Za-z][_0-9A-Za-z]*");

    /**
     * Validates that the Lexical token name matches the current spec.
     * currently non null, non empty,
     *
     * @param name - the name to be validated.
     *
     * @return the name if valid, or AssertException if invalid.
     */
    public static String assertValidName(String name) {
        if (name != null && !name.isEmpty() && validNamePattern.matcher(name).matches()) {
            return name;
        }
        return throwAssert(invalidNameErrorMessage, name);
    }

    private static <T> T throwAssert(String format, Object... args) {
        throw new AssertException(format(format, args));
    }
}
