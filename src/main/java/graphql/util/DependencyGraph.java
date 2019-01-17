/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import static graphql.Assert.assertShouldNeverHappen;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Basic DAG  (dependency graph) implementation
 * The main purpose of DependencyGraph is to perform topological sort of its vertices
 * 
 * @param <N> type of vertices in the DependencyGraph
 */
public class DependencyGraph<N extends Vertex<N>> {   
    public DependencyGraph () {
        this(16);
    }
    
    public DependencyGraph (int order) {
        this(new LinkedHashMap<>(order), new HashMap<>(order), 0);
    }
    
    public DependencyGraph (DependencyGraph<? super N> other) {
        this(new LinkedHashMap<>(Objects.requireNonNull(other).vertices), new HashMap<>(other.verticesById), other.nextId);
    }
    
    private DependencyGraph (Map<? super N, ? super N> vertices, Map<Object, ? super N> verticesById, int startId) {
        this.vertices = Objects.requireNonNull((Map<N, N>)vertices);
        this.verticesById = Objects.requireNonNull((Map<Object, N>)verticesById);
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
    
    protected DependencyGraph<N> addEdge (Edge<? extends N, ?> edge) {
        Objects.requireNonNull(edge);
        
        edges.add((Edge<N, ?>)edge);
        return this;
    }
  
    public DependencyGraph<N> addDependency (N maybeSink, N maybeSource) {
        return addDependency(maybeSink, maybeSource, Edge::emptyAction);
    }
    
    public <C extends DependencyGraphContext> DependencyGraph<N> addDependency (N maybeSink, N maybeSource, BiConsumer<? super C, ? super Edge<N, ?>> edgeAction) {
        // note reverse ordering of Vertex arguments.
        // an Edge points from source - to -> sink, we say "sink depends on source"
        return addEdge(new Edge<>(addNode(maybeSource), addNode(maybeSink), edgeAction));
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
    
    public DependenciesIterator<N> orderDependencies (DependencyGraphContext context) {
        return new DependenciesIteratorImpl(context);
    }
    
    protected class DependenciesIteratorImpl implements DependenciesIterator<N>, TraverserVisitor<N> {        
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
            Collection<N> nextClosure = Collections.newSetFromMap(new IdentityHashMap<>());
            return Optional
                .ofNullable((Collection<N>)traverser
                    .rootVar(Collection.class, nextClosure)
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
            currentClosure = Collections.emptySet();
            return closure;
        }

        @Override
        public void close(Collection<N> resolvedSet) {
            Objects.requireNonNull(resolvedSet);
            
            resolvedSet.forEach(this::closeNode);
            lastClosure = Collections.emptySet();
        }

        private boolean canResolve (N node) {
            return node.canResolve(context);
        }
        
        private void closeNode (N maybeNode) {
            N node = Optional
                .ofNullable(getNode(maybeNode))
                .orElseThrow(() -> new IllegalArgumentException("node not found: " + maybeNode));
            
            node.resolve(context);
            closed.add(node);
            unclosed.remove(node);
        }
        
        @Override
        public TraversalControl enter(TraverserContext<N> context) {
            Collection<N> closure = context.getParentContext().getVar(Collection.class);
            context
                .setVar(Collection.class, closure)    // to be propagated to children
                .setResult(closure);                  // to be returned
            
            N node = context.thisNode();
            if (canResolve(node)) {
                closeNode(node);
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
        
        DependenciesIteratorImpl (DependencyGraphContext context) {
            this.context = Objects.requireNonNull(context);
            
            unclosed.addAll(vertices.values());
        }
        
        final DependencyGraphContext context;
        final Collection<N> unclosed = Collections.newSetFromMap(new IdentityHashMap<>());
        final Collection<N> closed = Collections.newSetFromMap(new IdentityHashMap<>());
        final Traverser<N> traverser = Traverser.<N>breadthFirst(Vertex::adjacencySet, null);
        Collection<N> currentClosure = Collections.emptySet();
        Collection<N> lastClosure = Collections.emptySet();
    }
    
    protected int nextId = 0;
    protected final Map<N, N> vertices;
    protected final Map<Object, N> verticesById;
    protected final Set<Edge<N, ?>> edges = new AbstractSet<Edge<N, ?>>() {
        @Override
        public boolean add(Edge<N, ?> e) {
            Objects.requireNonNull(e);
            
            return e.connectEndpoints();
        }
        
        @Override
        public Iterator<Edge<N, ?>> iterator() {
            return new Iterator<Edge<N, ?>>() {
                @Override
                public boolean hasNext() {
                    boolean hasNext;
                    while (!(hasNext = current.hasNext()) && partitions.hasNext())
                        current = partitions.next();
                    
                    return hasNext;
                }

                @Override
                public Edge<N, ?> next() {
                    return (last = current.next());
                }

                @Override
                public void remove() {
                    current.remove();
                    last.disconnectEndpoints();
                }
                
                final Iterator<Iterator<Edge<N, ?>>> partitions = Stream.concat(
                        verticesById
                            .values()
                            .stream()
                            .map(v -> v.indegrees.iterator()),
                        Stream.of(Collections.<Edge<N, ?>>emptyIterator())
                    )
                    .collect(Collectors.toList())
                    .iterator();
                Iterator<Edge<N, ?>> current = partitions.next();
                Edge<N, ?> last;
            };
        }

        @Override
        public int size() {
            return verticesById
                .values()
                .stream()
                .collect(Collectors.summingInt(v -> v.indegrees.size()));
        }
    };
}
