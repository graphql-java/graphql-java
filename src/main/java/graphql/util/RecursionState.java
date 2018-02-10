package graphql.util;

import graphql.Internal;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotNull;

@Internal
abstract class RecursionState<T> {

    private final Deque<TraverserContext<?>> delegate;
    private final Map<T, Object> visitedMap = new ConcurrentHashMap<>();

    public RecursionState() {
        this(new ArrayDeque<>(32));
    }

    public RecursionState(Deque<? super TraverserContext<T>> delegate) {
        this.delegate = (Deque<TraverserContext<?>>) assertNotNull(delegate);
    }

    public TraverserContext<T> peek() {
        return (TraverserContext<T>) delegate.peek();
    }

    public abstract TraverserContext<T> pop();

    public abstract void pushAll(TraverserContext<T> o, Function<? super T, ? extends List<T>> getChildren);

    protected void addAll(Collection<? extends T> col) {
        assertNotNull(col).stream().map((x) -> newContext(x, null)).collect(Collectors.toCollection(() -> delegate));
    }

    protected boolean isEmpty() {
        return delegate.isEmpty();
    }

    protected void clear() {
        delegate.clear();
        visitedMap.clear();
    }

    protected TraverserContext<T> newContext(final T o, final TraverserContext<T> parent) {
        return new TraverserContext<T>() {
            @Override
            public T thisNode() {
                return o;
            }

            @Override
            public TraverserContext<T> parentContext() {
                return parent;
            }

            @Override
            public boolean isVisited(Object data) {
                return visitedMap.putIfAbsent(o, Optional.ofNullable(data).orElse(TraverserMarkers.NULL)) != null;
            }

            @Override
            public Map<T, Object> visitedNodes() {
                return visitedMap;
            }

            @Override
            public <S> S getVar(Class<? super S> key) {
                return (S) key.cast(vars.get(key));
            }

            @Override
            public <S> TraverserContext<T> setVar(Class<? super S> key, S value) {
                vars.put(key, value);
                return this;
            }

            final Map<Class<?>, Object> vars = new HashMap<>();
        };
    }

    protected Deque<TraverserContext<?>> getDelegate() {
        return delegate;
    }

    protected Map<T, Object> getVisitedMap() {
        return visitedMap;
    }
}
