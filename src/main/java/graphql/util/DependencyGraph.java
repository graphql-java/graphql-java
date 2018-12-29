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
    public DependencyGraph () {
        this(16, 16);
    }
    
    public DependencyGraph (int order, int size) {
        this(new LinkedHashMap<>(order), new HashMap<>(order), new LinkedHashSet<>(size), 0);
    }
    
    public DependencyGraph (DependencyGraph<? super N> other) {
        this(new LinkedHashMap<>(Objects.requireNonNull(other).vertices), new HashMap<>(other.verticesById), new LinkedHashSet<>(other.edges), other.nextId);
    }
    
    private DependencyGraph (Map<? super N, ? super N> vertices, Map<Object, ? super N> verticesById, Set<? super Edge<N>> edges, int startId) {
        this.vertices = Objects.requireNonNull((Map<N, N>)vertices);
        this.verticesById = Objects.requireNonNull((Map<Object, N>)verticesById);
        this.edges = Objects.requireNonNull((Set<Edge<N>>)edges);
        this.nextId = startId;
    }
    
    public N addNode (N maybeNode) {
        Objects.requireNonNull(maybeNode);
        
        return vertices.computeIfAbsent(maybeNode, 
            node -> {
                int id = nextId++;
                verticesById.put(id, node.id(id));
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
        Optional
            .ofNullable(addNode(maybeSink).dependsOn(addNode(maybeSource), edgeAction))
            .ifPresent(edges::add);
        
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
            Collection<N> nextClosure = new ArrayList<>();
            return Optional
                .ofNullable((Collection<N>)Traverser
                    .<N>breadthFirst(Vertex::adjacencySet, nextClosure)
                    .traverse(
                        unclosed
                            .stream()
                            .filter(node -> closed.containsAll(node.dependencySet()))
                            .collect(Collectors.toList()),
                        this
                    )            
                    .getResult())
                .orElse(nextClosure);
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
        public TraversalControl enter(TraverserContext<N> context) {
            Collection<N> closure = (Collection<N>)context.getInitialData();
            context.setResult(closure);
            
            N node = context.thisNode();
            if (closed.contains(node)) {
                return TraversalControl.CONTINUE;
            } else if (node.canResolve()) {
                node.resolve(node);
                return TraversalControl.CONTINUE;
            } else {
                closure.add(node);
                return TraversalControl.ABORT;
            }                           
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
    
    protected final Map<N, N> vertices;
    protected final Map<Object, N> verticesById;
    protected final Set<Edge<N>> edges;
    protected int nextId = 0;
}
