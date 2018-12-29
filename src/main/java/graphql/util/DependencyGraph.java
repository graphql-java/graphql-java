/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import static graphql.Assert.assertShouldNeverHappen;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 *
 * @param <N>
 */
public class DependencyGraph<N extends Vertex<N>> {   
    public N addNode (N maybeNode) {
        Objects.requireNonNull(maybeNode);
        
        return vertices.computeIfAbsent(maybeNode, 
            node -> {
                int id = nextId++;
                verticesById.put(id, node.outer(this).id(id));
                return node;
            });
    }
    
    public N getNode (N maybeNode) {
        return Optional
            .ofNullable(maybeNode.getId())
            .map(id -> Optional.ofNullable(verticesById.get(id)))
            .orElseGet(() -> Optional.ofNullable(vertices.get(maybeNode)))
            .orElse(null);
    }
  
    public DependencyGraph<N> addDependency (N maybeSink, N maybeSource) {
        return addDependency(maybeSink, maybeSource, (BiConsumer<N, N>)Edge.EMPTY_ACTION);
    }
    
    public DependencyGraph<N> addDependency (N maybeSink, N maybeSource, BiConsumer<? super N, ? super N> edgeAction) {
        addNode(maybeSink).dependsOn(addNode(maybeSource), edgeAction);
        return this;
    }
    
    public Collection<N> getDependencies (N maybeSource) {
        return Optional
            .ofNullable(getNode(maybeSource))
            .map(Vertex::dependencySet)
            .orElseThrow(() -> new IllegalArgumentException("Node " + maybeSource + " not found"));
    }
    
    public int order () {
        return vertices.size();
    }
    
    public int size () {
        return edges.size();
    }
    
    public TopOrderIterator<N> orderDependencies () {
        return new TopOrderIteratorImpl();
    }
    
    protected class TopOrderIteratorImpl implements TopOrderIterator<N>, TraverserVisitor<N> {
        @Override
        public boolean hasNext() {
            if (!lastClosure.isEmpty()) {
                // to let automatic advancing when external resolution is not needed
                close(lastClosure);
            }
            
            if (currentClosure.isEmpty()) {
                currentClosure = calculateNext();
            }

            boolean isDone = currentClosure.isEmpty();
            return (isDone && closed.size() != vertices.size())
                ? assertShouldNeverHappen("couldn't calculate next closure")
                : !isDone;
        }

        Collection<N> calculateNext () {
            return (Collection<N>)Traverser
                .<N>breadthFirst(Vertex::adjacencySet, new ArrayList<>())
                .traverse(
                    unclosed
                        .stream()
                        .filter(node -> closed.containsAll(node.dependencySet()))
                        .collect(Collectors.toList()),
                    this
                )
                .getResult();
        }

        @Override
        public Collection<N> next() {
            if (currentClosure.isEmpty())
                throw new NoSuchElementException("next closure hasn't been calculated yet");

            Collection<N> closure = lastClosure = currentClosure;
            currentClosure = Collections.emptyList();
            return closure;
        }

        @Override
        public void close(Collection<N> resolvedSet) {
            Objects.requireNonNull(resolvedSet);
            
            resolvedSet.forEach(this::closeNode);
            lastClosure = Collections.emptyList();
        }

        private void closeNode (N maybeNode) {
            N node = Optional
                .ofNullable(getNode(maybeNode))
                .orElseThrow(() -> new IllegalArgumentException("node not found: " + maybeNode));
            
            node.resolve(maybeNode);
            closed.add(node);
            unclosed.remove(node);
        }

        @Override
        public void start(TraverserContext<N> context) {
            context.setResult(context.getInitialData());
        }
        
        @Override
        public TraversalControl enter(TraverserContext<N> context) {
            Collection<N> closure = (Collection<N>)context.getInitialData();
            context.setResult(closure);
            
            N node = context.thisNode();
            if (closed.contains(node)) {
                return TraversalControl.CONTINUE;
            } else if (node.canResolve()) {
                node.resolve(node);
                return TraversalControl.CONTINUE;
            }                
            
            closure.add(node);
            return TraversalControl.ABORT;
        }

        @Override
        public TraversalControl leave(TraverserContext<N> context) {
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl backRef(TraverserContext<N> context) {
            assertShouldNeverHappen("cycle around node", context.thisNode());
            return TraversalControl.QUIT;
        }

        Collection<N> unclosed = new HashSet<>(vertices.values());
        Collection<N> closed = new HashSet<>();
        Collection<N> currentClosure = Collections.emptyList();
        Collection<N> lastClosure = Collections.emptyList();
    }
    
    protected DependencyGraph<N> addEdge (Edge<? super N> edge) {
        Objects.requireNonNull(edge);
        
        edges.add((Edge<N>)edge);
        return this;
    }
    
    public static <T> DependencyGraph<SimpleNode<T>> simpleGraph () {
        return new DependencyGraph<>();
    }
    
    protected final Map<N, N> vertices = new LinkedHashMap<>();
    protected final Map<Object, N> verticesById = new HashMap<>();
    protected final Set<Edge<N>> edges = new LinkedHashSet<>();
    protected int nextId = 0;
}
