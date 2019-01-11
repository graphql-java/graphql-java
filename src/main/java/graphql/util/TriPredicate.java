/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;

/**
 *
 * @author gkesler
 */
@FunctionalInterface
public interface TriPredicate<U, V, W> {
    /**
     * 
     * @param u
     * @param v
     * @param w
     * @return 
     */
    boolean test (U u, V v, W w);
    
    /**
     * 
     * @return 
     */
    default TriPredicate<U, V, W> negate () {
        return (U u, V v, W w) -> !test(u, v, w);
    }
    
    /**
     * 
     * @param other
     * @return 
     */
    default TriPredicate<U, V, W> and (TriPredicate<? super U, ? super V, ? super W> other) {
        Objects.requireNonNull(other);
        
        return (U u, V v, W w) -> test(u, v, w) && other.test(u, v, w);
    }
    
    /**
     * 
     * @param other
     * @return 
     */
    default TriPredicate<U, V, W> or (TriPredicate<? super U, ? super V, ? super W> other) {
        Objects.requireNonNull(other);
        
        return (U u, V v, W w) -> test(u, v, w) || other.test(u, v, w);
    }
}
