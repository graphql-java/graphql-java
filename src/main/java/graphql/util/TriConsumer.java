/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;

/**
 * Represents an operation that accepts three input arguments and returns no result. 
 * This is the three-arity specialization of {@link java.util.function.Consumer}. 
 * Unlike most other functional interfaces, TriConsumer is expected to operate via side-effects.
 * 
 * This is a functional interface whose functional method is accept(Object, Object).
 *
 * @param <U>   the type of the first argument to the operation
 * @param <V>   the type of the second argument to the operation
 * @param <W>   the type of the third argument to the operation
 */
@FunctionalInterface
public interface TriConsumer<U, V, W> {
    /**
     * Performs this operation on the given arguments.
     * 
     * @param u the first operation argument
     * @param v the second operation argument
     * @param w the third operation argument
     */
    void accept (U u, V v, W w);
    
    /**
     * Returns a composed TriConsumer that performs, in sequence, 
     * this operation followed by the after operation.If performing either 
     * operation throws an exception, it is relayed to the caller of the composed operation. 
     * 
     * If performing this operation throws an exception, the after operation will not be performed.
     * 
     * @param after the operation to perform after this operation
     * @return a composed TriConsumer that performs in sequence this operation followed by after operation
     * @throws NullPointerException if after is null
     */
    default TriConsumer<U, V, W> andThen (TriConsumer<? super U, ? super V, ? super W> after) {
        Objects.requireNonNull(after);
        
        return (U u, V v, W w) -> {
            accept(u, v, w);
            after.accept(u, v, w);
        };
    }
}
