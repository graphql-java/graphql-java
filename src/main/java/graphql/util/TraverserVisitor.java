package graphql.util;

import graphql.Internal;

/**
 * Visitor interface that get's notified as the Traverser traverses a tree
 *
 * @param <T> type of tree nodes
 * @param <U> type of data to pass or return across Visitor's methods
 */
@Internal
public interface TraverserVisitor<T, U> {
    /**
     * Notification that a traverser starts "visiting" a tree node
     *
     * @param context traverser context
     * @param data    a value to be passed to the visitor
     *
     * @return either a value to pass to next Visitor's method during traversal
     * or a marker to control the traversal
     *
     * @see TraverserContext
     */
    Object enter(TraverserContext<T> context, U data);

    /**
     * Notification that a traverser finishes "visiting" a tree node
     *
     * @param context traverser context
     * @param data    a value to be passed to the visitor
     *
     * @return either a value to pass to next Visitor's method during traversal
     * or a marker to control the traversal
     *
     * @see TraverserContext
     */
    Object leave(TraverserContext<T> context, U data);

    /**
     * Notification that a traverser visits a node it has already visited
     * This happens in cyclic graphs and the traversal does not traverse this
     * node again to prevent infinite recursion
     *
     * @param context traverser context
     * @param data    a value to be passed to the visitor
     *
     * @return either a value to pass to next Visitor's method during traversal
     * or a marker to control the traversal
     *
     * @see TraverserContext
     */
    default Object backRef(TraverserContext<T> context, U data) {
        return data;
    }

    /**
     * Notification that a traverser visits a map key associated with a child node
     * in case children are stored in a map vs. list. In this case call to this
     * method will be followed by {@link #enter(graphql.util.TraverserContext, java.lang.Object) method call}
     *
     * @param context traverser context
     * @param data    a value to be passed to the visitor
     *
     * @return either a value to pass to next Visitor's method during traversal
     * or a marker to control the traversal
     *
     * @see TraverserContext
     */
    default Object mapKey(TraverserContext<T> context, U data) {
        return data;
    }
}
