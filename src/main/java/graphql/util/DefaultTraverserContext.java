package graphql.util;

import com.google.common.collect.ImmutableList;
import graphql.Internal;
import graphql.collect.ImmutableKit;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static graphql.Assert.assertFalse;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertNull;
import static graphql.Assert.assertTrue;

@Internal
public class DefaultTraverserContext<T> implements TraverserContext<T> {

    private final T curNode;
    private T newNode;
    private boolean nodeDeleted;

    private final TraverserContext<T> parent;
    private final Set<T> visited;
    private @Nullable Map<Class<?>, Object> vars;
    private final Object sharedContextData;

    private Object newAccValue;
    private boolean hasNewAccValue;
    private Object curAccValue;
    private final NodeLocation location;
    private final boolean isRootContext;
    private Map<String, List<TraverserContext<T>>> children;
    private Phase phase;
    private final Breadcrumb<T> breadcrumb;
    private volatile List<Breadcrumb<T>> breadcrumbs;

    public DefaultTraverserContext(T curNode,
                                   TraverserContext<T> parent,
                                   Set<T> visited,
                                   @Nullable Map<Class<?>, Object> vars,
                                   Object sharedContextData,
                                   NodeLocation location,
                                   boolean isRootContext) {
        this.curNode = curNode;
        this.parent = parent;
        this.visited = visited;
        this.vars = vars;
        this.sharedContextData = sharedContextData;
        this.location = location;
        this.isRootContext = isRootContext;

        if (parent == null || parent.isRootContext()) {
            this.breadcrumb = null;
            this.breadcrumbs = ImmutableKit.emptyList();
        } else {
            // Capture the parent now: visitors can change or delete it before breadcrumbs are requested.
            this.breadcrumb = new Breadcrumb<>(parent.thisNode(), location);
            if (parent.getClass() != DefaultTraverserContext.class) {
                this.breadcrumbs = ImmutableList.<Breadcrumb<T>>builderWithExpectedSize(parent.getBreadcrumbs().size() + 1)
                        .add(breadcrumb)
                        .addAll(parent.getBreadcrumbs())
                        .build();
            }
        }
    }

    public static <T> DefaultTraverserContext<T> dummy() {
        return new DefaultTraverserContext<>(null, null, null, null, null, null, true);
    }

    public static <T> DefaultTraverserContext<T> simple(T node) {
        return new DefaultTraverserContext<>(node, null, null, null, null, null, true);
    }

    @Override
    public T thisNode() {
        assertFalse(this.nodeDeleted, "node is deleted");
        if (newNode != null) {
            return newNode;
        }
        return curNode;
    }

    @Override
    public T originalThisNode() {
        return curNode;
    }

    @Override
    public void changeNode(T newNode) {
        assertNotNull(newNode);
        assertFalse(this.nodeDeleted, "node is deleted");
        this.newNode = newNode;
    }


    @Override
    public void deleteNode() {
        assertNull(this.newNode, "node is already changed");
        assertFalse(this.nodeDeleted, "node is already deleted");
        this.nodeDeleted = true;
    }

    @Override
    public boolean isDeleted() {
        return this.nodeDeleted;
    }

    @Override
    public boolean isChanged() {
        return this.newNode != null;
    }


    @Override
    public TraverserContext<T> getParentContext() {
        return parent;
    }

    @Override
    public List<T> getParentNodes() {
        List<T> result = new ArrayList<>();
        TraverserContext<T> curContext = parent;
        while (!curContext.isRootContext()) {
            result.add(curContext.thisNode());
            curContext = curContext.getParentContext();
        }
        return result;
    }

    @Override
    public List<Breadcrumb<T>> getBreadcrumbs() {
        if (breadcrumbs == null) {
            ImmutableList.Builder<Breadcrumb<T>> builder = ImmutableList.builder();
            DefaultTraverserContext<T> context = this;
            while (context.breadcrumbs == null) {
                builder.add(context.breadcrumb);
                context = (DefaultTraverserContext<T>) context.parent;
            }
            breadcrumbs = builder.addAll(context.breadcrumbs).build();
        }
        return breadcrumbs;
    }

    @Override
    public T getParentNode() {
        if (parent == null) {
            return null;
        }
        return parent.thisNode();
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
        if (vars == null) {
            return null;
        }
        return (S) key.cast(vars.get(key));
    }

    @Override
    public <S> TraverserContext<T> setVar(Class<? super S> key, S value) {
        if (vars == null) {
            vars = new LinkedHashMap<>();
        }
        vars.put(key, value);
        return this;
    }

    @Override
    public void setAccumulate(Object accumulate) {
        hasNewAccValue = true;
        newAccValue = accumulate;
    }

    @Override
    public <U> U getNewAccumulate() {
        if (hasNewAccValue) {
            return (U) newAccValue;
        } else {
            return (U) curAccValue;
        }
    }

    @Override
    public <U> U getCurrentAccumulate() {
        return (U) curAccValue;
    }


    @Override
    public Object getSharedContextData() {
        return sharedContextData;
    }

    /*
     * PRIVATE: Used by {@link Traverser}
     */
    void setCurAccValue(Object curAccValue) {
        hasNewAccValue = false;
        this.curAccValue = curAccValue;
    }

    @Override
    public NodeLocation getLocation() {
        return location;
    }

    @Override
    public boolean isRootContext() {
        return isRootContext;
    }

    @Override
    public <S> S getVarFromParents(Class<? super S> key) {
        TraverserContext<T> curContext = parent;
        while (curContext != null) {
            S var = curContext.getVar(key);
            if (var != null) {
                return var;
            }
            curContext = curContext.getParentContext();
        }
        return null;
    }

    /*
     * PRIVATE: Used by {@link Traverser}
     */
    void setChildrenContexts(Map<String, List<TraverserContext<T>>> children) {
        assertTrue(this.children == null, "children already set");
        this.children = children;
    }


    @Override
    public Map<String, List<TraverserContext<T>>> getChildrenContexts() {
        assertNotNull(children, "children not available");
        return children;
    }

    /*
     * PRIVATE: Used by {@link Traverser}
     */
    void setPhase(Phase phase) {
        this.phase = phase;
    }

    @Override
    public Phase getPhase() {
        return phase;
    }
}
