package graphql.execution;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * We just want to be able to call a method that returns a value but doesn't throw any checked exceptions.
 *
 * @param <T>
 */
@Internal
@NullMarked
@FunctionalInterface
public interface CallableWithoutException<T> {
    T call();
}
