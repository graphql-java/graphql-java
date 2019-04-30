/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Objects;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an edge between two vertices in the DependencyGraph
 * The direction of edge is from source -- to --&gt; sink
 * This is opposite from the represented dependency direction, e.g.from sink -- to --&gt; source
 * 
 * @param <N> the actual Vertex subtype used
 * @param <E> the Edge subtype
 */
public class Edge<N extends Vertex<N>, E extends Edge<N, E>> {
    protected Edge (N source, N sink) {
        this(source, sink, Edge::emptyAction);
    }
    
    protected <C extends DependencyGraphContext> Edge (N source, N sink, BiConsumer<? super C, ? super E> action) {
        this.source = Objects.requireNonNull(source, "From Vertex MUST be specified");
        this.sink = Objects.requireNonNull(sink, "To Vertex MUST be specified");
        this.action = Objects.requireNonNull((BiConsumer<DependencyGraphContext, E>)action, "Edge action MUST be specified");
    }  
    
    public N getSource () {
        return source;
    }
    
    public N getSink () {
        return sink;
    }

    public BiConsumer<DependencyGraphContext, E> getAction() {
        return action;
    }
    
    protected boolean connectEndpoints () {
        if (source != sink) {// do not record dependency on the same vertex
            return source.indegrees.add(this) &&
                    sink.outdegrees.add(this);
        } else {
            LOGGER.warn("ignoring short circuit dependency: {}", this);
            return false;
        }
    }
    
    protected boolean disconnectEndpoints () {
        return source.indegrees.remove(this) &&
            sink.outdegrees.remove(this);
    }
    
    protected void fire (DependencyGraphContext context) {
        action.accept(context, (E)this);
    }
    
    static void emptyAction (DependencyGraphContext context, Edge<?, ?> edge) {
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
        final Edge<?, ?> other = (Edge<?, ?>) obj;
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
        return toString(new StringBuilder(getClass().getSimpleName()).append('{'))
                .append('}')
                .toString();
    }
    
    protected StringBuilder toString (StringBuilder builder) {
        return builder
                .append("source=").append(source)
                .append(", sink=").append(sink)
                .append(", action=").append(action);
    }
    
    protected final N source;
    protected final N sink;
    protected final BiConsumer<DependencyGraphContext, E> action;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Edge.class);
}
