/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphql.util;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import static java.util.Collections.newSetFromMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
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
        this(new LinkedHashMap<>(assertNotNull(other).vertices), new HashMap<>(other.verticesById), other.nextId);
    }
    
    private DependencyGraph (Map<? super N, ? super N> vertices, Map<Object, ? super N> verticesById, int startId) {
        this.vertices = assertNotNull((Map<N, N>)vertices);
        this.verticesById = assertNotNull((Map<Object, N>)verticesById);
        this.nextId = startId;
    }
    
    public <U extends N> U addNode (U maybeNode) {
        assertNotNull(maybeNode);
        
        return (U)vertices.computeIfAbsent(maybeNode, 
            node -> {
                int id = nextId++;
                verticesById.put(id, node.id(id));
                return node;
            });
    }
    
    public <U extends N> U getNode (U maybeNode) {
        return (U)Optional
            .ofNullable(maybeNode.getId())
            .map(id -> Optional.ofNullable(verticesById.get(id)))
            .orElseGet(() -> Optional.ofNullable(vertices.get(maybeNode)))
            .orElse(null);
    }
    
    protected DependencyGraph<N> addEdge (Edge<? extends N, ?> edge) {
        assertNotNull(edge);
        
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

        Set<N> calculateNext () {
            Set<N> nextClosure = closureCreator.get();
            return (Set<N>)Optional
                .ofNullable(traverser
                    .rootVar(Collection.class, nextClosure)
                    .traverse(
                        unclosed
                            .stream()
                            .filter(v -> closed.containsAll(v.dependencySet()))
                            .collect(Collectors.toList()), 
                        this
                    )            
                    .getAccumulatedResult())
                .orElse(nextClosure);
        }

        @Override
        public Collection<N> next() {
            if (currentClosure.isEmpty())
                throw new NoSuchElementException("next closure hasn't been calculated yet");

            lastClosure = currentClosure;
            currentClosure = Collections.emptySet();
            return lastClosure;
        }

        @Override
        public void close(N node) {
            closeResolved(node);
        }
        
        private boolean closeNode (N maybeNode, boolean autoResolve) {
            N node = Optional
                .ofNullable(getNode(maybeNode))
                .orElseThrow(() -> new IllegalArgumentException("node not found: " + maybeNode));
            
            if (node.resolve(context) || !autoResolve) {
                closed.add(node);
                unclosed.remove(node);
                lastClosure.remove(node);
                
                node.fireResolved(context);
                return true;
            }
            
            return false;
        }

        private boolean closeResolved (N maybeNode) {
            return closeNode(maybeNode, false/*autoClose*/);
        }
        
        private boolean autoClose (N maybeNode) {
            return closeNode(maybeNode, true/*autoResolve*/);
        }
        
        @Override
        public TraversalControl enter(TraverserContext<N> context) {
            TraverserContext<N> parentContext = context.getParentContext();
            Collection<N> closure = parentContext.getVar(Collection.class);
            context
                .setVar(Collection.class, closure)    // to be propagated to children
                .setAccumulate(closure);                  // to be returned
            
            N node = context.thisNode();
            if (parentContext.thisNode() == null || closed.containsAll(node.dependencySet())) {
                if (autoClose(node)) {
                    return TraversalControl.CONTINUE;
                } else {
                    closure.add(node);
                    return TraversalControl.ABORT;
                }                           
            } else {
                context
                    .visitedNodes()
                    .remove(node);
                return TraversalControl.ABORT;
            }
        }

        @Override
        public TraversalControl leave(TraverserContext<N> context) {
            return TraversalControl.CONTINUE;
        }

        @Override
        public TraversalControl backRef(TraverserContext<N> context) {
            assertShouldNeverHappen("cycle around node with id={}", context.thisNode().getId());
            return TraversalControl.QUIT;
        }
        
        protected DependenciesIteratorImpl (DependencyGraphContext context, Supplier<Set<N>> closureCreator) {
            this.context = assertNotNull(context);
            this.closureCreator = assertNotNull(closureCreator);
            this.unclosed = closureCreator.get();
            this.closed = closureCreator.get();
            
            unclosed.addAll(vertices.values());
        }

        protected DependenciesIteratorImpl (DependencyGraphContext context) {
            this(context, () -> newSetFromMap(new IdentityHashMap<>()));
        }
        
        final DependencyGraphContext context;
        final Supplier<Set<N>> closureCreator;
        final Collection<N> unclosed;
        final Collection<N> closed;
        final Traverser<N> traverser = Traverser.<N>breadthFirst(Vertex::adjacencySet, null);
        Set<N> currentClosure = Collections.emptySet();
        Set<N> lastClosure = Collections.emptySet();
    }
    
    protected int nextId = 0;
    protected final Map<N, N> vertices;
    protected final Map<Object, N> verticesById;
    protected final Set<Edge<N, ?>> edges = new AbstractSet<Edge<N, ?>>() {
        @Override
        public boolean add(Edge<N, ?> e) {
            assertNotNull(e);
            
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
