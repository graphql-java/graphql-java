package graphql.util;

import graphql.Internal;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;

@Internal
public abstract class TraverserState<T> {

    private Object sharedContextData;

    private final Deque<Object> state;
    private final Set<T> visited = new LinkedHashSet<>();


    private static class StackTraverserState<U> extends TraverserState<U> {

        private StackTraverserState(Object initialData) {
            super(initialData);
        }

        @Override
        public void pushAll(TraverserContext<U> traverserContext, Function<? super U, ? extends List<U>> getChildren) {
            super.state.push(traverserContext);
            super.state.push(Marker.END_LIST);
            List<U> children = getChildren.apply(traverserContext.thisNode());
            for (int i = children.size() - 1; i >= 0; i--) {
                U child = children.get(i);
                NodePosition nodePosition = new NodePosition(null, i);
                super.state.push(super.newContext(child, traverserContext, nodePosition));
            }
        }
    }

    private static class QueueTraverserState<U> extends TraverserState<U> {

        private QueueTraverserState(Object initialData) {
            super(initialData);
        }

        @Override
        public void pushAll(TraverserContext<U> traverserContext, Function<? super U, ? extends List<U>> getChildren) {
            List<U> children = getChildren.apply(traverserContext.thisNode());
            for (int i = 0; i < children.size(); i++) {
                U child = children.get(i);
                NodePosition nodePosition = new NodePosition(null, i);
                super.state.add(super.newContext(child, traverserContext, nodePosition));
            }
            super.state.add(Marker.END_LIST);
            super.state.add(traverserContext);
        }
    }

    public enum Marker {
        END_LIST
    }

    private TraverserState(Object sharedContextData) {
        this.sharedContextData = sharedContextData;
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


    public void addNewContexts(Collection<? extends T> children, TraverserContext<T> parentContext) {
        assertNotNull(children).stream().map((child) -> newContext(child, parentContext, null)).forEach(this.state::add);
    }

    public boolean isEmpty() {
        return state.isEmpty();
    }


    public void addVisited(T visited) {
        this.visited.add(visited);
    }

    private DefaultTraverserContext<T> newContext(T o, TraverserContext<T> parent, NodePosition position) {
        return newContextWithVars(o, parent, new LinkedHashMap<>(), position);
    }

    public DefaultTraverserContext<T> newContextWithVars(T curNode, TraverserContext<T> parent, Map<Class<?>, Object> vars, NodePosition nodePosition) {
        assertNotNull(vars);
        return new DefaultTraverserContext<>(curNode, parent, visited, vars, sharedContextData, nodePosition);
    }
}
