package graphql.util;

import graphql.Internal;

/**
 * Special traversal control values
 */
@Internal
public enum TraversalControl {

    CONTINUE,
    /**
     * When returned from a Visitor's method, indicates exiting the traversal
     */
    QUIT,
    /**
     * When returned from a Visitor's method, indicates skipping traversal of a subtree
     */
    ABORT,
}
