package graphql.util;

import graphql.Internal;

import java.util.function.Supplier;

import static graphql.Assert.assertNotNull;

/**
 * This memoizing supplier does NOT use synchronised double locking to set its value
 * so on multiple threads it MAY call the delegate again to get a value.
 *
 * @param <T> for two
 */
@Internal
class IntraThreadMemoizedSupplier<T> implements Supplier<T> {
    private final static Object SENTINEL = new Object() {
    };

    @SuppressWarnings("unchecked")
    private T value = (T) SENTINEL;
    private final Supplier<T> delegate;

    IntraThreadMemoizedSupplier(Supplier<T> delegate) {
        this.delegate = assertNotNull(delegate);
    }

    @Override
    public T get() {
        T t = value;
        if (t == SENTINEL) {
            t = delegate.get();
            value = t;
        }
        return t;
    }
}
