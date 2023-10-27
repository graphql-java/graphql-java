package graphql.util;

import graphql.Internal;

/**
 * This class is useful for creating a mutable reference to a variable that can be changed when you are in an
 * effectively final bit of code.  Its more performant than an {@link java.util.concurrent.atomic.AtomicReference}
 * to gain mutability.  Use this very carefully - Its not expected to be commonly used.
 *
 * @param <T> for two
 */
@Internal
public class MutableRef<T> {
    public T value;
}
