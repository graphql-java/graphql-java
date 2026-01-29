package graphql;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Supplier;

import static java.lang.String.format;

@SuppressWarnings("TypeParameterUnusedInFormals")
@Internal
@NullMarked
public class Assert {

    public static <T> T assertNotNullWithNPE(T object, Supplier<String> msg) {
        if (object != null) {
            return object;
        }
        throw new NullPointerException(msg.get());
    }

    public static <T> T assertNotNullWithNPE(T object, String constantMsg) {
        if (object != null) {
            return object;
        }
        throw new NullPointerException(constantMsg);
    }

    @Contract("null -> fail")
    public static <T> T assertNotNull(@Nullable T object) {
        if (object != null) {
            return object;
        }
        return throwAssert("Object required to be not null");
    }

    @Contract("null,_ -> fail")
    public static <T> T assertNotNull(@Nullable T object, Supplier<String> msg) {
        if (object != null) {
            return object;
        }
        return throwAssert(msg.get());
    }

    @Contract("null,_ -> fail")
    public static <T> T assertNotNull(@Nullable T object, String constantMsg) {
        if (object != null) {
            return object;
        }
        return throwAssert(constantMsg);
    }

    @Contract("null,_,_ -> fail")
    public static <T> T assertNotNull(@Nullable T object, String msgFmt, Object arg1) {
        if (object != null) {
            return object;
        }
        return throwAssert(msgFmt, arg1);
    }

    @Contract("null,_,_,_ -> fail")
    public static <T> T assertNotNull(@Nullable T object, String msgFmt, Object arg1, Object arg2) {
        if (object != null) {
            return object;
        }
        return throwAssert(msgFmt, arg1, arg2);
    }

    @Contract("null,_,_,_,_ -> fail")
    public static <T> T assertNotNull(@Nullable T object, String msgFmt, Object arg1, Object arg2, Object arg3) {
        if (object != null) {
            return object;
        }
        return throwAssert(msgFmt, arg1, arg2, arg3);
    }


    @Contract("!null,_ -> fail")
    public static <T> void assertNull(@Nullable T object, Supplier<String> msg) {
        if (object == null) {
            return;
        }
        throwAssert(msg.get());
    }

    @Contract("!null,_ -> fail")
    public static <T> void assertNull(@Nullable T object, String constantMsg) {
        if (object == null) {
            return;
        }
        throwAssert(constantMsg);
    }

    @Contract("!null -> fail")
    public static <T> void assertNull(@Nullable Object object) {
        if (object == null) {
            return;
        }
        throwAssert("Object required to be null");
    }

    @Contract("-> fail")
    public static <T> T assertNeverCalled() {
        return throwAssert("Should never been called");
    }

    @Contract("_,_-> fail")
    public static <T> T assertShouldNeverHappen(String format, Object... args) {
        return throwAssert("Internal error: should never happen: %s", format(format, args));
    }

    @Contract("-> fail")
    public static <T> T assertShouldNeverHappen() {
        return throwAssert("Internal error: should never happen");
    }

    public static <T> Collection<T> assertNotEmpty(Collection<T> collection) {
        if (collection == null || collection.isEmpty()) {
            throwAssert("collection must be not null and not empty");
        }
        return collection;
    }

    //    @Contract("null,_-> fail")
    public static <T> Collection<T> assertNotEmpty(Collection<T> collection, Supplier<String> msg) {
        if (collection == null || collection.isEmpty()) {
            throwAssert(msg.get());
        }
        return collection;
    }

    public static <T> Collection<T> assertNotEmpty(Collection<T> collection, String constantMsg) {
        if (collection == null || collection.isEmpty()) {
            throwAssert(constantMsg);
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

    /**
     * Validates that the Lexical token name matches the current spec.
     * currently non null, non empty,
     *
     * @param name - the name to be validated.
     *
     * @return the name if valid, or AssertException if invalid.
     */
    public static String assertValidName(String name) {
        if (name != null && !name.isEmpty() && isValidName(name)) {
            return name;
        }
        return throwAssert(invalidNameErrorMessage, name);
    }

    /**
     * Fast character-by-character validation without regex.
     * Checks if name matches [_A-Za-z][_0-9A-Za-z]*
     */
    private static boolean isValidName(String name) {
        if (name.isEmpty()) {
            return false;
        }

        // First character must be [_A-Za-z]
        char first = name.charAt(0);
        if (!(first == '_' || (first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z'))) {
            return false;
        }

        // Remaining characters must be [_0-9A-Za-z]
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
                return false;
            }
        }

        return true;
    }

    private static <T> T throwAssert(String format, Object... args) {
        throw new AssertException(format(format, args));
    }
}
