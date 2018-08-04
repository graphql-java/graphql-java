package graphql.execution.lazy;

import graphql.ExperimentalApi;
import graphql.PublicSpi;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A simplified lazy list that always passes {@literal true} as the boolean argument. See the documentation of
 * {@link LazyList} for more details.
 *
 * This interface is useful because it can be assigned from {@literal stream::forAll} where {@literal stream} is an
 * instance of {@link java.util.stream.Stream}.
 *
 * @param <T> the type of object passed to the callback
 */
@PublicSpi
@ExperimentalApi
@FunctionalInterface
public interface BlockingLazyList<T> extends LazyList<T> {
    /**
     * This is called to retrieve the elements of the lazy list. The callback should be invoked once for each element in
     * the list.
     *
     * If the callback throws an exception, the exception should be passed through.
     *
     * @param action the callback to invoke
     */
    void forEach(Consumer<? super T> action);

    default void forEach(BiConsumer<Boolean, ? super T> action) {
        forEach(item -> action.accept(true, item));
    }
}
