/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Collection;
import java.util.Iterator;

/**
 * Iterator over dependent vertices in their topological order
 * 
 * @param <N> the actual Vertex subtype used
 */
public interface DependenciesIterator<N extends Vertex<N>> extends Iterator<Collection<N>> {  
    /**
     * Marks provided vertices as resolved, so their dependent vertices will be selected
     * in the next iteration.
     * 
     * @see java.util.Iterator#next() 
     * 
     * @param resolvedSet vertices to be marked as resolved
     */
    void close (Collection<N> resolvedSet);
}
