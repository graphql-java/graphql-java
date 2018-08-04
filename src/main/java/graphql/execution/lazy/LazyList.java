package graphql.execution.lazy;

import graphql.ExecutionResult;
import graphql.ExperimentalApi;
import graphql.PublicSpi;

import java.util.function.BiConsumer;

/**
 * A lazy list can be returned by a data fetcher of a field of list type to defer the completion of the list until
 * the elements are actually needed. Returning a lazy list from a data fetcher causes the execution result to contain a
 * lazy list at the same place. In order to retrieve the completed list elements, the lazy list in the execution result
 * must be invoked.
 *
 * Since the completion of the list elements is deferred, not all possible errors are known when the execution result is
 * created. Therefore, the {@link ExecutionResult#toSpecification()} method cannot be used if lazy lists are used during
 * the execution. Instead the user must manually serialize the execution result in accordance with the specification.
 * The errors of the execution result must only be retrieved once all elements of all lazy lists have been completed.
 *
 * @param <T> the type of object passed to the callback
 */
@PublicSpi
@ExperimentalApi
@FunctionalInterface
public interface LazyList<T> {

    /**
     * This is called to retrieve the elements of the lazy list. The callback should be invoked once for each element in
     * the list. The boolean argument is only used for lazy lists returned by the data fetcher (in contrast to the lazy
     * list contained in the execution result.) In this case, all elements passed to the callback are completed
     * concurrently until the callback is invoked with the boolean argument true. Once it is invoked with true, the
     * execution waits for all elements that have not yet been passed along to complete and then passes them along.
     *
     * If the callback throws an exception, the exception should be passed through.
     *
     * @param action the callback to invoke
     */
    void forEach(BiConsumer<Boolean, ? super T> action);
}
