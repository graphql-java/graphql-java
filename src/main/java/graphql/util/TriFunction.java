/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a function that accepts three arguments and produces a result. 
 * This is the tree-arity specialization of {@link java.util.function.Function}.
 * 
 * This is a functional interface whose functional method is apply(Object, Object, Object).
 * 
 * @param <U>   the type of the first argument to the function
 * @param <V>   the type of the second argument to the function
 * @param <W>   the type of the third argument to the function
 * @param <R>   the type of the result of the function
 */
@FunctionalInterface
public interface TriFunction<U, V, W, R> {
    /**
     * Applies this function to the given arguments.
     * 
     * @param u the first function argument
     * @param v the second function argument
     * @param w the third function argument
     * @return the function result
     */
    R apply (U u, V v, W w);
    
    /**
     * Returns a composed function that first applies this function to its input, 
     * and then applies the after function to the result.
     * 
     * If evaluation of either function throws an exception, 
     * it is relayed to the caller of the composed function.
     * 
     * @param <X>   the type of output of the after funciton and the composed function
     * 
     * @param after the function to apply after this function is applied
     * @return the composed function that first applies this function and 
     * then applies the after function
     * @throws NullPointerException if after is null
     */
    default <X> TriFunction<U, V, W, X> andThen (Function<? super R, ? extends X> after) {
        Objects.requireNonNull(after);
        
        return (U u, V v, W w) -> after.apply(apply(u, v, w));
    }
}
