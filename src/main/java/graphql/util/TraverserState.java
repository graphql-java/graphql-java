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

@Internal
public abstract class TraverserState<T> {

    private Object initialData;


    private final Deque<Object> state;
    private final Set<T> visited = new LinkedHashSet<>();


    private static class StackTraverserState<U> extends TraverserState<U> {

        private StackTraverserState(Object initialData) {
            super(initialData);
        }

        @Override
        public void pushAll(TraverserContext<U> o, Function<? super U, ? extends List<U>> getChildren) {
            super.state.push(o);
            super.state.push(Marker.END_LIST);
            new ReverseIterator<>(getChildren.apply(o.thisNode())).forEachRemaining((e) -> super.state.push(newContext(e, o)));
        }
    }

    private static class QueueTraverserState<U> extends TraverserState<U> {

        private QueueTraverserState(Object initialData) {
            super(initialData);
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
        this.initialData = initialData;
        this.state = new ArrayDeque<>(32);
    }

    public static <U> TraverserState<U> newQueueState(Object initialData) {
        return new QueueTraverserState<>(initialData);
    }

    public static <U> TraverserState<U> newStackState(Object initialData) {
        return new StackTraverserState<>(initialData);
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
        assertNotNull(vars);

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
            public Object getParentResult() {
                return parent.getResult();
            }

            @Override
            public Set<T> visitedNodes() {
                return visited;
            }

            @Override
            public boolean isVisited() {
                return visited.contains(curNode);
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

            @Override
            public void setResult(Object result) {
                this.result = result;
            }

            @Override
            public Object getResult() {
                return this.result;
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
}
