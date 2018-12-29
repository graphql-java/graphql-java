/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @param <N> 
 */
public abstract class Vertex<N extends Vertex<N>> {
    public Object getId () {
        return id;
    }
    
    protected N id (Object id) {
        this.id = id;
        return (N)this;
    }
    
    protected N outer (DependencyGraph<? super N> outer) {
        this.outer = Objects.requireNonNull((DependencyGraph<N>)outer);
        return (N)this;
    }
    
    public N dependsOn (N source, BiConsumer<? super N, ? super N> edgeAction) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(edgeAction);
        
        if (this != source) {// do not record dependency on itself
            Edge<N> edge = new Edge<>(source, (N)this, edgeAction);
            outer.addEdge(edge);

            this.outdegrees.add(edge);
            source.indegrees.add(edge);
        } else {
            LOGGER.warn("ignoring cyclic dependency on itself: {}", this);
        }
        
        return (N)this;
    }
    
    public List<N> adjacencySet () {
        return indegrees
            .stream()
            .map(Edge::getSink)
            .collect(Collectors.toList());
    }
    
    public List<N> dependencySet () {
        return outdegrees
            .stream()
            .map(Edge::getSource)
            .collect(Collectors.toList());
    }
    
    public void fireResolved () {
        indegrees.forEach(Edge::fire);
    }
    
    public boolean canResolve () {
        return false;
    }
    
    public void resolve (N resultNode) {
        fireResolved();
    }
    
    @Override
    public String toString() {
        return toString(new StringBuilder(getClass().getSimpleName()).append('{'))
                .append('}')
                .toString();
    }
    
    protected StringBuilder toString (StringBuilder builder) {
        return builder
            .append("id=").append(id)
            .append(", dependencies=").append(
                outdegrees
                    .stream()
                    .map(Edge::getSource)
                    .map(Vertex::toString)
                    .collect(Collectors.joining(", ", "on ->", ""))
            );
    }
    
    protected DependencyGraph<N> outer;
    protected Object id;
    protected final Set<Edge<N>> outdegrees = new LinkedHashSet<>();
    protected final Set<Edge<N>> indegrees = new LinkedHashSet<>();
    
    private static final Logger LOGGER = LoggerFactory.getLogger(Vertex.class);
}
