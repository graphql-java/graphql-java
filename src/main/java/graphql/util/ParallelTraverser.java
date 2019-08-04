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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.function.Function;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;
import static graphql.util.TraversalControl.ABORT;
import static graphql.util.TraversalControl.CONTINUE;
import static graphql.util.TraversalControl.QUIT;

@Internal
public class ParallelTraverser<T> {

    private final Function<? super T, Map<String, ? extends List<T>>> getChildren;
    private final Map<Class<?>, Object> rootVars = new ConcurrentHashMap<>();


    private Object sharedContextData;


    private ParallelTraverser(Function<? super T, Map<String, ? extends List<T>>> getChildren, Object sharedContextData) {
        this.getChildren = assertNotNull(getChildren);
        this.sharedContextData = sharedContextData;
    }

    public static <T> ParallelTraverser<T> parallelTraverser(Function<? super T, ? extends List<T>> getChildren) {
        return parallelTraverser(getChildren, null);
    }

    public static <T> ParallelTraverser<T> parallelTraverser(Function<? super T, ? extends List<T>> getChildren, Object sharedContextData) {
        return new ParallelTraverser<>(wrapListFunction(getChildren), sharedContextData);
    }

    public static <T> ParallelTraverser<T> parallelTraverserWithNamedChildren(Function<? super T, Map<String, ? extends List<T>>> getNamedChildren, Object sharedContextData) {
        return new ParallelTraverser<>(getNamedChildren, sharedContextData);
    }


    private static <T> Function<? super T, Map<String, ? extends List<T>>> wrapListFunction(Function<? super T, ? extends List<T>> listFn) {
        return node -> {
            List<T> childs = listFn.apply(node);
            return Collections.singletonMap(null, childs);
        };
    }

    public ParallelTraverser<T> rootVars(Map<Class<?>, Object> rootVars) {
        this.rootVars.putAll(assertNotNull(rootVars));
        return this;
    }

    public ParallelTraverser<T> rootVar(Class<?> key, Object value) {
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
        ForkJoinPool.commonPool().invoke(new RecursiveAction() {
            @Override
            protected void compute() {
                List<ForkJoinTask> tasks = new ArrayList<>();
                for (T root : roots) {
                    DefaultTraverserContext context = newContext(root, rootContext, null);
                    EnterAction enterAction = new EnterAction(context, visitor);
                    tasks.add(enterAction);
                }
                invokeAll(tasks);
            }
        });
    }

    private class EnterAction extends RecursiveAction {
        private DefaultTraverserContext currentContext;
        private TraverserVisitor<? super T> visitor;

        private EnterAction(DefaultTraverserContext currentContext, TraverserVisitor<? super T> visitor) {
            this.currentContext = currentContext;
            this.visitor = visitor;
        }

        @Override
        public void compute() {
            currentContext.setPhase(TraverserContext.Phase.ENTER);
            TraversalControl traversalControl = visitor.enter(currentContext);
            assertNotNull(traversalControl, "result of enter must not be null");
            assertTrue(QUIT != traversalControl, "can't return QUIT for parallel traversing");
            if (ABORT == traversalControl) {
                return;
            }
            assertTrue(traversalControl == CONTINUE);
            List<DefaultTraverserContext> children = pushAll(currentContext);
            List<EnterAction> subTasks = new ArrayList<>();
            for (int i = 0; i < children.size(); i++) {
                subTasks.add(new EnterAction(children.get(i), visitor));
            }
            invokeAll(subTasks);

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
                    T child = assertNotNull(children.get(i), "null child for key %s", key);
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
        return new DefaultTraverserContext<>(curNode, parent, null, vars, sharedContextData, nodeLocation, isRootContext);
    }
}


