package graphql.util;

import graphql.Assert;

import java.util.Map;

/**
 * Special traversal control values
 */
public enum TraverserMarkers implements TraverserContext<Object> {
    /**
     * Used instead of {@code null} when storing a value in a map
     */
    NULL,
    /**
     * When returned from a Visitor's method, indicates exiting the traversal
     */
    QUIT,
    /**
     * When returned from a Visitor's method, indicates skipping traversal of a subtree
     */
    ABORT,
    /**
     * A special value placed into a {@link RecursionState} to indicate end of children list of
     * a particular node. The very next value in the {@link RecursionState} is the parent node context
     */
    END_LIST,
    /**
     * A special value placed into a {@link RecursionState} to indicate a name/value pair,
     * where the very next element in the {@link RecursionState} is a key immediately followed
     * by a child node stored under that key in its parent's association.
     */
    MAP_KEY;

    @Override
    public Object thisNode() {
        return Assert.assertShouldNeverHappen();
    }

    @Override
    public TraverserContext<Object> parentContext() {
        return Assert.assertShouldNeverHappen();
    }

    @Override
    public boolean isVisited(Object data) {
        return Assert.assertShouldNeverHappen();
    }

    @Override
    public Map<Object, Object> visitedNodes() {
        return Assert.assertShouldNeverHappen();
    }

    @Override
    public <S> S getVar(Class<? super S> key) {
        return Assert.assertShouldNeverHappen();
    }

    @Override
    public <S> TraverserContext<Object> setVar(Class<? super S> key, S value) {
        return Assert.assertShouldNeverHappen();
    }
}
