/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;
import java.util.function.Function;

/**
 *
 * @author gkesler
 * @param <U>
 * @param <V>
 * @param <W>
 * @param <R>
 */
@FunctionalInterface
public interface TriFunction<U, V, W, R> {
    /**
     * 
     * @param u
     * @param v
     * @param w
     * @return 
     */
    R apply (U u, V v, W w);
    
    /**
     * 
     * @param <T>
     * @param after
     * @return 
     */
    default <T> TriFunction<U, V, W, T> andThen (Function<? super R, ? extends T> after) {
        Objects.requireNonNull(after);
        
        return (U u, V v, W w) -> after.apply(apply(u, v, w));
    }
}
