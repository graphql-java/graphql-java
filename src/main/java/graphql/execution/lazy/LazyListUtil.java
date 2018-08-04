package graphql.execution.lazy;

import graphql.Internal;

import java.util.function.Function;

/**
 * A utility for wrapping lazy lists in other lazy lists in a fluent style
 *
 * @param <T> the type parameter of the lazy list
 */
@Internal
public class LazyListUtil<T> {
    private final LazyList<T> delegate;

    private LazyListUtil(LazyList<T> delegate) {
        this.delegate = delegate;
    }

    public static <T> LazyListUtil<T> of(LazyList<T> delegate) {
        return new LazyListUtil<>(delegate);
    }

    public <U> LazyListUtil<U> wrap(Function<LazyList<T>, LazyList<U>> f) {
        return LazyListUtil.of(f.apply(delegate));
    }

    public LazyList<T> unwrap() {
        return delegate;
    }
}
