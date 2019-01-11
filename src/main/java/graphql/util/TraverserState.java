package graphql.util;

import graphql.Internal;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

@Internal
public abstract class TraverserState<T> {

    private Object initialData;


    private final Deque<Object> state;
    private final Set<T> visited = new LinkedHashSet<>();
    private final Function<TraverserContextBuilder<T>, TraverserContext<T>> contextFactory;


    public static class StackTraverserState<U> extends TraverserState<U> {

        private StackTraverserState(Object initialData) {
            super(initialData);
        }
        
        private StackTraverserState(Object initialData, Function<TraverserContextBuilder<U>, TraverserContext<U>> contextFactory) {
            super(initialData, contextFactory);
        }

        @Override
        public void pushAll(TraverserContext<U> o, Function<? super U, ? extends List<U>> getChildren) {
            super.state.push(o);
            super.state.push(Marker.END_LIST);
            new ReverseIterator<>(getChildren.apply(o.thisNode())).forEachRemaining((e) -> super.state.push(newContext(e, o)));
        }
    }

    public static class QueueTraverserState<U> extends TraverserState<U> {

        private QueueTraverserState(Object initialData) {
            super(initialData);
        }

        private QueueTraverserState(Object initialData, Function<TraverserContextBuilder<U>, TraverserContext<U>> contextFactory) {
            super(initialData, contextFactory);
        }
        
        @Override
        public void pushAll(TraverserContext<U> o, Function<? super U, ? extends List<U>> getChildren) {
            getChildren.apply(o.thisNode()).iterator().forEachRemaining((e) -> super.state.add(newContext(e, o)));
            super.state.add(Marker.END_LIST);
            super.state.add(o);
        }
    }

    public enum Marker {
        END_LIST
    }

    private TraverserState(Object initialData) {
        this(initialData, TraverserState::newContext);
    }
    
    private TraverserState(Object initialData, Function<TraverserContextBuilder<T>, TraverserContext<T>> contextFactory) {
        this.initialData = initialData;
        this.state = new ArrayDeque<>(32);
        this.contextFactory = Objects.requireNonNull(contextFactory);
    }

    public static <U> QueueTraverserState<U> newQueueState(Object initialData) {
        return new QueueTraverserState<>(initialData);
    }

    public static <U> QueueTraverserState<U> newQueueState(Object initialData, Function<TraverserContextBuilder<U>, TraverserContext<U>> contextFactory) {
        return new QueueTraverserState<>(initialData, contextFactory);
    }

    public static <U> StackTraverserState<U> newStackState(Object initialData) {
        return new StackTraverserState<>(initialData);
    }

    public static <U> StackTraverserState<U> newStackState(Object initialData, Function<TraverserContextBuilder<U>, TraverserContext<U>> contextFactory) {
        return new StackTraverserState<>(initialData, contextFactory);
    }

    public abstract void pushAll(TraverserContext<T> o, Function<? super T, ? extends List<T>> getChildren);

    public Object pop() {
        return this.state.pop();
    }


    public void addNewContexts(Collection<? extends T> children, TraverserContext<T> root) {
        assertNotNull(children).stream().map((x) -> newContext(x, root)).forEach(this.state::add);
    }

    public boolean isEmpty() {
        return state.isEmpty();
    }


    public void addVisited(T visited) {
        this.visited.add(visited);
    }

    public TraverserContext<T> newContext(T o, TraverserContext<T> parent) {
        return newContext(o, parent, new LinkedHashMap<>());
    }

    public TraverserContext<T> newContext(T curNode, TraverserContext<T> parent, Map<Class<?>, Object> vars) {
        return new TraverserContextBuilder<>(this)
            .thisNode(curNode)
            .parentContext(parent)
            .vars(vars)
            .build(contextFactory);
    }
    
    private static <T> TraverserContext<T> newContext (TraverserContextBuilder<T> builder) {
        assertNotNull(builder);

        T curNode = builder.getNode();
        TraverserContext<T> parent = builder.getParentContext();
        Map<Class<?>, Object> vars = builder.getVars();
        Set<T> visited = builder.getVisited();
        Object initialData = builder.getInitialData();
        
        return new TraverserContext<T>() {
            Object result;

            @Override
            public T thisNode() {
                return curNode;
            }

            @Override
            public TraverserContext<T> getParentContext() {
                return parent;
            }

            @Override
            public Set<T> visitedNodes() {
                return visited;
            }

            @Override
            public <S> S computeVarIfAbsent(Class<? super S> key, BiFunction<? super TraverserContext<T>, ? super Class<S>, ? extends S> provider) {
                assertNotNull(provider);
                
                return (S) key.cast(vars.computeIfAbsent(key, k -> provider.apply(this, (Class<S>)k)));
            }

            @Override
            public <S> TraverserContext<T> setVar(Class<? super S> key, S value) {
                vars.put(key, value);
                return this;
            }

            @Override
            public void setResult(Object result) {
                this.result = result;
            }

            @Override
            public Object computeResultIfAbsent(Function<? super TraverserContext<T>, ? extends Object> provider) {
                Objects.requireNonNull(provider);
                
                return Optional
                    .ofNullable(this.result)
                    .orElseGet(() -> provider.apply(this));
            }

            @Override
            public Object getInitialData() {
                return initialData;
            }
        };
    }

    private static class ReverseIterator<T> implements Iterator<T> {
        private final ListIterator<T> delegate;

        private ReverseIterator(List<T> list) {
            assertNotNull(list);

            this.delegate = list.listIterator(list.size());
        }

        @Override
        public boolean hasNext() {
            return delegate.hasPrevious();
        }

        @Override
        public T next() {
            return delegate.previous();
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }
    
    public static final class TraverserContextBuilder<T> {
        private final TraverserState<T> outer;
        
        private /*final*/ T node;
        private /*final*/ TraverserContext<T> parentContext;
        private /*final*/ Map<Class<?>, Object> vars;
        
        public TraverserContextBuilder (TraverserState<T> outer) {
            this.outer = Objects.requireNonNull(outer);
        }
        
        public TraverserContext<T> build (Function<? super TraverserContextBuilder<T>, ? extends TraverserContext<T>> creator) {
            Objects.requireNonNull(creator);
            return creator.apply(this);
        }
        
        public TraverserContextBuilder<T> thisNode (T node) {
            this.node = node;
            return this;
        }
        
        public TraverserContextBuilder<T> parentContext (TraverserContext<? super T> parentContext) {
            this.parentContext = (TraverserContext<T>)parentContext;
            return this;
        }
        
        public TraverserContextBuilder<T> vars (Map<Class<?>, Object> vars) {
            this.vars = Objects.requireNonNull(vars);
            return this;
        }

        public T getNode() {
            return node;
        }

        public TraverserContext<T> getParentContext() {
            return parentContext;
        }

        public Map<Class<?>, Object> getVars() {
            return vars;
        }
        
        public Object getInitialData () {
            return outer.initialData;
        }
        
        public Set<T> getVisited () {
            return outer.visited;
        }
    }
}
