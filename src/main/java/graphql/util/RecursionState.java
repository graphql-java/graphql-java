package graphql.util;

import graphql.Assert;
import graphql.Internal;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;

@Internal
public class RecursionState<T> {

    private Object initialData;

    public enum Type {
        STACK,
        QUEUE
    }

    private final Deque<Object> state;
    private final Set<T> visited = new LinkedHashSet<>();
    private final Type type;


    public enum Marker {
        END_LIST
    }

    public RecursionState(Type type, Object initialData) {
        this.initialData = initialData;
        this.state = new ArrayDeque<>(32);
        this.type = assertNotNull(type);
    }


    public Object peek() {
        return state.peek();
    }


    public Object pop() {
        return this.state.pop();
    }

    public void pushAll(TraverserContext<T> o, Function<? super T, ? extends List<T>> getChildren) {
        if (type == Type.QUEUE) {
            getChildren.apply(o.thisNode()).iterator().forEachRemaining((e) -> this.state.add(newContext(e, o)));
            this.state.add(Marker.END_LIST);
            this.state.add(o);
        } else if (type == Type.STACK) {
            this.state.push(o);
            this.state.push(Marker.END_LIST);
            new ArrayDeque<>(getChildren.apply(o.thisNode())).descendingIterator().forEachRemaining((e) -> this.state.push(newContext(e, o)));
        } else {
            Assert.assertShouldNeverHappen();
        }

    }


    public void addNewContexts(Collection<? extends T> children, TraverserContext<T> root) {
        assertNotNull(children).stream().map((x) -> newContext(x, root)).forEach(this.state::add);
    }

    public boolean isEmpty() {
        return state.isEmpty();
    }

    public void clear() {
        state.clear();
        visited.clear();
    }

    public TraverserContext<T> newContext(T o, TraverserContext<T> parent) {
        return newContext(o, parent, new ConcurrentHashMap<>());
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
            public TraverserContext<T> parentContext() {
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


}
