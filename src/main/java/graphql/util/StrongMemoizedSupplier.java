package graphql.util;

import graphql.Internal;

import java.util.function.Supplier;

@Internal
public class StrongMemoizedSupplier<T> implements Supplier<T> {

    private final Supplier<T> delegate;
    private volatile boolean initialized;
    private T value;

    public StrongMemoizedSupplier(Supplier<T> delegate) {
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
