package graphql.util;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A lazy value that is computed once and once only.  Useful for effectively final values
 * that dont need to be computed on creation but on first time use.
 *
 * @param <T> for two
 */
public final class LazyReference<T> {

    private final Supplier<T> valueSupplier;
    private volatile T value;

    public LazyReference(Supplier<T> valueSupplier) {
        this.valueSupplier = requireNonNull(valueSupplier);
    }

    /**
     * @return the computed lazy value
     */
    public T get() {
        final T result = value;
        return result == null ? maybeCompute(valueSupplier) : result;
    }

    private synchronized T maybeCompute(Supplier<T> valueSupplier) {
        if (value == null) {
            value = requireNonNull(valueSupplier.get());
        }
        return value;
    }

    @Override
    public int hashCode() {
        return get().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) {
            return get().equals(obj);
        }
        LazyReference<?> that = (LazyReference<?>) obj;
        return this.get().equals(that.get());
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
