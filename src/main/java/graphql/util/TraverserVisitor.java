package graphql.util;

import graphql.Internal;

/**
 * Visitor interface that get's notified as the Traverser traverses a tree
 *
 * @param <T> type of tree nodes
 * @param <U> type of data to pass or return across Visitor's methods
 */
@Internal
public interface TraverserVisitor<T> {
    /**
     * Notification that a traverser starts "visiting" a tree node
     *
     * @param context traverser context
     *
     * @return either a value to pass to next Visitor's method during traversal
     * or a marker to control the traversal
     *
     * @see TraverserContext
     */
    TraversalControl enter(TraverserContext<T> context);

    /**
     * Notification that a traverser finishes "visiting" a tree node
     *
     * @param context traverser context
     *
     * @return either a value to pass to next Visitor's method during traversal
     * or a marker to control the traversal
     *
     * @see TraverserContext
     */
    TraversalControl leave(TraverserContext<T> context);

    /**
     * Notification that a traverser visits a node it has already visited
     * This happens in cyclic graphs and the traversal does not traverse this
     * node again to prevent infinite recursion
     *
     * @param context traverser context
     *
     * @return either a value to pass to next Visitor's method during traversal
     * or a marker to control the traversal
     *
     * @see TraverserContext
     */
    default TraversalControl backRef(TraverserContext<T> context) {
        return null;
    }

}
