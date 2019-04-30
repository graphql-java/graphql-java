/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;

/**
 * Represents a predicate (boolean-valued function) of three arguments. 
 * This is the three-arity specialization of {@link java.util.function.Predicate}.
 * 
 * This is a functional interface whose functional method is test(Object, Object).
 *
 * @param <U>   the type of the first argument to the operation
 * @param <V>   the type of the second argument to the operation
 * @param <W>   the type of the third argument to the operation
 */
@FunctionalInterface
public interface TriPredicate<U, V, W> {
    /**
     * Evaluates this predicate on the given arguments
     * 
     * @param u the first operation argument
     * @param v the second operation argument
     * @param w the third operation argument
     * @return {@code true} if the input arguments match the predicate, {@code false} otherwise.
     */
    boolean test (U u, V v, W w);
    
    /**
     * Returns a predicate that represents the logical negation of this predicate.
     * 
     * @return a predicate that represents the logical negation of this predicate.
     */
    default TriPredicate<U, V, W> negate () {
        return (U u, V v, W w) -> !test(u, v, w);
    }
    
    /**
     * Returns a composed predicate that represents a short-circuiting logical AND 
     * of this predicate and another. When evaluating the composed predicate, 
     * if this predicate is false, then the other predicate is not evaluated.
     * 
     * Any exceptions thrown during evaluation of either predicate are relayed to 
     * the caller; if evaluation of this predicate throws an exception, 
     * the other predicate will not be evaluated.
     * 
     * @param other a predicate that will be logically-ANDed with this predicate
     * @return a composed predicate that represents the short-circuiting logical AND 
     * of this predicate and the other predicate
     * @throws NullPointerException if other is null
     */
    default TriPredicate<U, V, W> and (TriPredicate<? super U, ? super V, ? super W> other) {
        Objects.requireNonNull(other);
        
        return (U u, V v, W w) -> test(u, v, w) && other.test(u, v, w);
    }
    
    /**
     * Returns a composed predicate that represents a short-circuiting logical OR 
     * of this predicate and another. When evaluating the composed predicate, 
     * if this predicate is true, then the other predicate is not evaluated.
     * 
     * Any exceptions thrown during evaluation of either predicate are relayed to 
     * the caller; if evaluation of this predicate throws an exception, 
     * the other predicate will not be evaluated.
     * 
     * @param other a predicate that will be logically-ORed with this predicate
     * @return a composed predicate that represents the short-circuiting logical OR 
     * of this predicate and the other predicate
     * @throws NullPointerException if other is null
     */
    default TriPredicate<U, V, W> or (TriPredicate<? super U, ? super V, ? super W> other) {
        Objects.requireNonNull(other);
        
        return (U u, V v, W w) -> test(u, v, w) || other.test(u, v, w);
    }
}
