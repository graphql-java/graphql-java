/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 *
 * @param <N>
 */
public class Edge<N extends Vertex<N>> {
    protected Edge (N source, N sink) {
        this(source, sink, (BiConsumer<N, N>)EMPTY_ACTION);
    }
    
    protected Edge (N source, N sink, BiConsumer<? super N, ? super N> action) {
        this.source = Objects.requireNonNull(source, "From Vertex MUST be specified");
        this.sink = Objects.requireNonNull(sink, "To Vertex MUST be specified");
        this.action = Objects.requireNonNull((BiConsumer<N, N>)action, "Edge action MUST be specified");
    }  
    
    public N getSource () {
        return source;
    }
    
    public N getSink () {
        return sink;
    }
    
    public void fire () {
        action.accept(source, sink);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 89 * hash + Objects.hashCode(this.source);
        hash = 89 * hash + Objects.hashCode(this.sink);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Edge<?> other = (Edge<?>) obj;
        if (!Objects.equals(this.source, other.source)) {
            return false;
        }
        if (!Objects.equals(this.sink, other.sink)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Edge{" + "source=" + source + ", sink=" + sink + ", action=" + action + '}';
    }
    
    protected final N source;
    protected final N sink;
    protected final BiConsumer<N, N> action;
    
    public static final BiConsumer<?, ?> EMPTY_ACTION = (from, to) -> {};
}
