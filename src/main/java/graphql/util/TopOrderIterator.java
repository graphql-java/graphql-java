/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Collection;
import java.util.Iterator;

/**
 *
 * @author gkesler
 * @param <N>
 */
public interface TopOrderIterator<N extends Vertex<N>> extends Iterator<Collection<N>> {  
    void close (Collection<N> resolvedSet);
}
