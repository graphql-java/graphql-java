package graphql.util;

import graphql.Internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import java.util.Objects;

@Internal
public abstract class TraverserState<T> {

    private Object sharedContextData;

    private final Deque<Object> state;
    private final Set<T> visited = new LinkedHashSet<>();
    private final Function<TraverserContextBuilder<T>, DefaultTraverserContext<T>> contextFactory;


    public static class StackTraverserState<U> extends TraverserState<U> {

        private StackTraverserState(Object sharedContextData) {
            super(sharedContextData);
        }
        
        private StackTraverserState(Object sharedContextData, Function<TraverserContextBuilder<U>, DefaultTraverserContext<U>> contextFactory) {
            super(sharedContextData, contextFactory);
        }

        @Override
        public void pushAll(TraverserContext<U> traverserContext, Function<? super U, Map<String, ? extends List<U>>> getChildren) {
            super.state.push(traverserContext);

            EndList<U> endList = new EndList<>();
            super.state.push(endList);
            Map<String, List<TraverserContext<U>>> childrenContextMap = new LinkedHashMap<>();

            if (!traverserContext.isDeleted()) {
                Map<String, ? extends List<U>> childrenMap = getChildren.apply(traverserContext.thisNode());
                childrenMap.keySet().forEach(key -> {
                    List<U> children = childrenMap.get(key);
                    for (int i = children.size() - 1; i >= 0; i--) {
                        U child = assertNotNull(children.get(i), "null child for key " + key);
                        NodeLocation nodeLocation = new NodeLocation(key, i);
                        TraverserContext<U> context = super.newContext(child, traverserContext, nodeLocation);
                        super.state.push(context);
                        childrenContextMap.computeIfAbsent(key, notUsed -> new ArrayList<>());
                        childrenContextMap.get(key).add(0, context);
                    }
                });
            }
            endList.childrenContextMap = childrenContextMap;
        }
    }

    public static class QueueTraverserState<U> extends TraverserState<U> {

        private QueueTraverserState(Object sharedContextData) {
            super(sharedContextData);
        }

        private QueueTraverserState(Object sharedContextData, Function<TraverserContextBuilder<U>, DefaultTraverserContext<U>> contextFactory) {
            super(sharedContextData, contextFactory);
        }
        
        @Override
        public void pushAll(TraverserContext<U> traverserContext, Function<? super U, Map<String, ? extends List<U>>> getChildren) {
            Map<String, List<TraverserContext<U>>> childrenContextMap = new LinkedHashMap<>();
            if (!traverserContext.isDeleted()) {
                Map<String, ? extends List<U>> childrenMap = getChildren.apply(traverserContext.thisNode());
                childrenMap.keySet().forEach(key -> {
                    List<U> children = childrenMap.get(key);
                    for (int i = 0; i < children.size(); i++) {
                        U child = assertNotNull(children.get(i), "null child for key " + key);
                        NodeLocation nodeLocation = new NodeLocation(key, i);
                        TraverserContext<U> context = super.newContext(child, traverserContext, nodeLocation);
                        childrenContextMap.computeIfAbsent(key, notUsed -> new ArrayList<>());
                        childrenContextMap.get(key).add(context);
                        super.state.add(context);
                    }
                });
            }
            EndList<U> endList = new EndList<>();
            endList.childrenContextMap = childrenContextMap;
            super.state.add(endList);
            super.state.add(traverserContext);
        }
    }

    public static class EndList<U> {
        public Map<String, List<TraverserContext<U>>> childrenContextMap;
    }

    private TraverserState(Object initialData) {
        this(initialData, TraverserState::newContext);
    }
    
    private TraverserState(Object sharedContextData, Function<TraverserContextBuilder<T>, DefaultTraverserContext<T>> contextFactory) {
        this.sharedContextData = sharedContextData;
        this.state = new ArrayDeque<>(32);
        this.contextFactory = assertNotNull(contextFactory);
    }

    public static <U> QueueTraverserState<U> newQueueState(Object initialData) {
        return new QueueTraverserState<>(initialData);
    }

    public static <U> QueueTraverserState<U> newQueueState(Object initialData, Function<TraverserContextBuilder<U>, DefaultTraverserContext<U>> contextFactory) {
        return new QueueTraverserState<>(initialData, contextFactory);
    }

    public static <U> StackTraverserState<U> newStackState(Object initialData) {
        return new StackTraverserState<>(initialData);
    }

    public static <U> StackTraverserState<U> newStackState(Object initialData, Function<TraverserContextBuilder<U>, DefaultTraverserContext<U>> contextFactory) {
        return new StackTraverserState<>(initialData, contextFactory);
    }

    public abstract void pushAll(TraverserContext<T> o, Function<? super T, Map<String, ? extends List<T>>> getChildren);

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

    private static <T> DefaultTraverserContext<T> newContext (TraverserContextBuilder<T> builder) {
        assertNotNull(builder);

        return new DefaultTraverserContext<>(
            builder.getNode(), 
            builder.getParentContext(), 
            builder.getVisited(), 
            builder.getVars(), 
            builder.getSharedContextData(), 
            builder.getNodeLocation(), 
            builder.isRootContext()
        );
    }

    public TraverserContext<T> newRootContext(Map<Class<?>, Object> vars) {
        return newContextImpl(null, null, vars, null, true);
    }

    private TraverserContext<T> newContext(T o, TraverserContext<T> parent, NodeLocation position) {
        return newContextImpl(o, parent, new LinkedHashMap<>(), position, false);
    }

    private TraverserContext<T> newContextImpl(T curNode,
                                                      TraverserContext<T> parent,
                                                      Map<Class<?>, Object> vars,
                                                      NodeLocation nodeLocation,
                                                      boolean isRootContext) {
        assertNotNull(vars);
        
        return new TraverserContextBuilder<>(this)
            .thisNode(curNode)
            .parentContext(parent)
            .vars(vars)
            .nodeLocation(nodeLocation)
            .rootContext(isRootContext)
            .build(contextFactory);
    }
    
    public static final class TraverserContextBuilder<T> {
        private final TraverserState<T> outer;
        
        private /*final*/ T node;
        private /*final*/ TraverserContext<T> parentContext;
        private /*final*/ Map<Class<?>, Object> vars;
        private /*final*/ NodeLocation nodeLocation;
        boolean /*final*/ isRootContext;
        
        public TraverserContextBuilder (TraverserState<T> outer) {
            this.outer = Objects.requireNonNull(outer);
        }
        
        public DefaultTraverserContext<T> build (Function<? super TraverserContextBuilder<T>, ? extends DefaultTraverserContext<T>> creator) {
            assertNotNull(creator);
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

        public TraverserContextBuilder<T> rootContext (boolean value) {
            this.isRootContext = value;
            return this;
        }
        
        public TraverserContextBuilder<T> nodeLocation (NodeLocation nodeLocation) {
            this.nodeLocation = nodeLocation;
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
        
        public Object getSharedContextData () {
            return outer.sharedContextData;
        }
        
        public Set<T> getVisited () {
            return outer.visited;
        }

        public boolean isRootContext() {
            return isRootContext;
        }

        public NodeLocation getNodeLocation() {
            return nodeLocation;
        }
       
    }
}
