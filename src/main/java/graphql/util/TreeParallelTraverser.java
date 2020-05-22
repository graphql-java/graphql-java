package graphql.util;

import graphql.Internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.util.TraversalControl.ABORT;
import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TraversalControl.QUIT;

@Internal
public class TreeParallelTraverser<T> {

    private final Function<? super T, Map<String, ? extends List<T>>> getChildren;
    private final Map<Class<?>, Object> rootVars = new ConcurrentHashMap<>();

    private final ForkJoinPool forkJoinPool;

    private Object sharedContextData;


    private TreeParallelTraverser(Function<? super T, Map<String, ? extends List<T>>> getChildren,
                                  Object sharedContextData,
                                  ForkJoinPool forkJoinPool) {
        this.getChildren = assertNotNull(getChildren);
        this.sharedContextData = sharedContextData;
        this.forkJoinPool = forkJoinPool;
    }

    public static <T> TreeParallelTraverser<T> parallelTraverser(Function<? super T, ? extends List<T>> getChildren) {
        return parallelTraverser(getChildren, null, ForkJoinPool.commonPool());
    }

    public static <T> TreeParallelTraverser<T> parallelTraverser(Function<? super T, ? extends List<T>> getChildren,
                                                                 Object sharedContextData) {
        return new TreeParallelTraverser<>(wrapListFunction(getChildren), sharedContextData, ForkJoinPool.commonPool());
    }

    public static <T> TreeParallelTraverser<T> parallelTraverser(Function<? super T, ? extends List<T>> getChildren,
                                                                 Object sharedContextData,
                                                                 ForkJoinPool forkJoinPool) {
        return new TreeParallelTraverser<>(wrapListFunction(getChildren), sharedContextData, forkJoinPool);
    }


    public static <T> TreeParallelTraverser<T> parallelTraverserWithNamedChildren(Function<? super T, Map<String, ? extends List<T>>> getNamedChildren,
                                                                                  Object sharedContextData) {
        return new TreeParallelTraverser<>(getNamedChildren, sharedContextData, ForkJoinPool.commonPool());
    }

    public static <T> TreeParallelTraverser<T> parallelTraverserWithNamedChildren(Function<? super T, Map<String, ? extends List<T>>> getNamedChildren,
                                                                                  Object sharedContextData,
                                                                                  ForkJoinPool forkJoinPool) {
        return new TreeParallelTraverser<>(getNamedChildren, sharedContextData, forkJoinPool);
    }


    private static <T> Function<? super T, Map<String, ? extends List<T>>> wrapListFunction(Function<? super T, ? extends List<T>> listFn) {
        return node -> {
            List<T> childs = listFn.apply(node);
            return Collections.singletonMap(null, childs);
        };
    }

    public TreeParallelTraverser<T> rootVars(Map<Class<?>, Object> rootVars) {
        this.rootVars.putAll(assertNotNull(rootVars));
        return this;
    }

    public TreeParallelTraverser<T> rootVar(Class<?> key, Object value) {
        rootVars.put(key, value);
        return this;
    }

    public void traverse(T root, TraverserVisitor<? super T> visitor) {
        traverse(Collections.singleton(root), visitor);
    }

    public void traverse(Collection<? extends T> roots, TraverserVisitor<? super T> visitor) {
        traverseImpl(roots, visitor);
    }

    public DefaultTraverserContext<T> newRootContext(Map<Class<?>, Object> vars) {
        return newContextImpl(null, null, vars, null, true);
    }


    public void traverseImpl(Collection<? extends T> roots, TraverserVisitor<? super T> visitor) {
        assertNotNull(roots);
        assertNotNull(visitor);

        DefaultTraverserContext<T> rootContext = newRootContext(rootVars);
        forkJoinPool.invoke(new CountedCompleter<Void>() {
            @Override
            public void compute() {
                setPendingCount(roots.size());
                for (T root : roots) {
                    DefaultTraverserContext context = newContext(root, rootContext, null);
                    EnterAction enterAction = new EnterAction(this, context, visitor);
                    enterAction.fork();
                }
                tryComplete();
            }

        });
    }

    private class EnterAction extends CountedCompleter {
        private DefaultTraverserContext currentContext;
        private TraverserVisitor<? super T> visitor;

        private EnterAction(CountedCompleter parent, DefaultTraverserContext currentContext, TraverserVisitor<? super T> visitor) {
            super(parent);
            this.currentContext = currentContext;
            this.visitor = visitor;
        }

        @Override
        public void compute() {
            currentContext.setPhase(TraverserContext.Phase.ENTER);
            TraversalControl traversalControl = visitor.enter(currentContext);
            assertNotNull(traversalControl, () -> "result of enter must not be null");
            assertTrue(QUIT != traversalControl, () -> "can't return QUIT for parallel traversing");
            if (traversalControl == ABORT) {
                tryComplete();
                return;
            }
            assertTrue(traversalControl == CONTINUE);
            List<DefaultTraverserContext> children = pushAll(currentContext);
            if (children.size() == 0) {
                tryComplete();
                return;
            }
            setPendingCount(children.size() - 1);
            for (int i = 1; i < children.size(); i++) {
                new EnterAction(this, children.get(i), visitor).fork();
            }
            new EnterAction(this, children.get(0), visitor).compute();
        }
    }

    private List<DefaultTraverserContext> pushAll(TraverserContext<T> traverserContext) {

        Map<String, List<TraverserContext<T>>> childrenContextMap = new LinkedHashMap<>();

        LinkedList<DefaultTraverserContext> contexts = new LinkedList<>();
        if (!traverserContext.isDeleted()) {

            Map<String, ? extends List<T>> childrenMap = getChildren.apply(traverserContext.thisNode());
            childrenMap.keySet().forEach(key -> {
                List<T> children = childrenMap.get(key);
                for (int i = children.size() - 1; i >= 0; i--) {
                    T child = assertNotNull(children.get(i), () -> String.format("null child for key %s", key));
                    NodeLocation nodeLocation = new NodeLocation(key, i);
                    DefaultTraverserContext<T> context = newContext(child, traverserContext, nodeLocation);
                    contexts.push(context);
                    childrenContextMap.computeIfAbsent(key, notUsed -> new ArrayList<>());
                    childrenContextMap.get(key).add(0, context);
                }
            });
        }
        return contexts;
    }

    private DefaultTraverserContext<T> newContext(T o, TraverserContext<T> parent, NodeLocation position) {
        return newContextImpl(o, parent, new LinkedHashMap<>(), position, false);
    }

    private DefaultTraverserContext<T> newContextImpl(T curNode,
                                                      TraverserContext<T> parent,
                                                      Map<Class<?>, Object> vars,
                                                      NodeLocation nodeLocation,
                                                      boolean isRootContext) {
        assertNotNull(vars);
        return new DefaultTraverserContext<>(curNode, parent, null, vars, sharedContextData, nodeLocation, isRootContext, true);
    }
}


