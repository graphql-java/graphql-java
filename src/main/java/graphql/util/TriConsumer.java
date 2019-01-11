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
 * @param <U>
 * @param <V>
 * @param <W>
 */
@FunctionalInterface
public interface TriConsumer<U, V, W> {
    /**
     * 
     * @param u
     * @param v
     * @param w 
     */
    void accept (U u, V v, W w);
    
    default TriConsumer<U, V, W> andThen (TriConsumer<? super U, ? super V, ? super W> after) {
        Objects.requireNonNull(after);

        return (U u, V v, W w) -> { accept(u, v, w); after.accept(u, v, w); };
    }
}
