package graphql.util;

import graphql.Internal;

@Internal
public interface TraverserVisitor<T> {

    /**
     * @param context the context in place
     *
     * @return Any allowed control value
     */
    TraversalControl enter(TraverserContext<T> context);

    /**
     * @param context the context in place
     *
     * @return Only Continue or Quit allowed
     */
    TraversalControl leave(TraverserContext<T> context);

    /**
     * @param context the context in place
     *
     * @return Only Continue or Quit allowed
     */
    default TraversalControl backRef(TraverserContext<T> context) {
        return TraversalControl.CONTINUE;
    }

    /**
     * Notifies visitor when traverser is about to start 
     * 
     * @param context the very root context
     */
    default void start (TraverserContext<T> context) {
    }
    
    /**
     * Notifies visitor when traverser has stopped traversing
     * 
     * @param context the context in place
     */
    default void finish (TraverserContext<T> context) {
    }
}
