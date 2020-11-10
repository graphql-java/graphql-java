package graphql.util;

import graphql.Internal;

import java.util.function.Supplier;

/**
 * This memoizing supplier DOES use synchronised double locking to set its value.
 *
 * @param <T> for two
 */
@Internal
public class InterThreadMemoizedSupplier<T> implements Supplier<T> {

    private final Supplier<T> delegate;
    private volatile boolean initialized;
    private T value;

    public InterThreadMemoizedSupplier(Supplier<T> delegate) {
        this.delegate = delegate;
    }


    @Override
    public T get() {
        if (!initialized) {
            synchronized (this) {
                if (initialized) {
                    return value;
                }
                value = delegate.get();
                initialized = true;
                return value;
            }
        }
        return value;
    }
}
