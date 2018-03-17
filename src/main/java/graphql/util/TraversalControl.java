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
     * When returned from a Visitor's method, indicates skipping traversal of a subtree.
     *
     * Not allowed to be returned from 'leave' or 'backRef' because it doesn't make sense.
     *
     */
    ABORT
}
