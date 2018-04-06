package graphql.util;

import graphql.Internal;

@Internal
public interface TraverserVisitor<T> {

    TraversalControl enter(TraverserContext<T> context);

    /**
     * @param context   {@link TraverserContext} for T
     * @return Only Continue or Quit allowed
     */
    TraversalControl leave(TraverserContext<T> context);

    /**
     * @param context   {@link TraverserContext} for T
     * @return Only Continue or Quit allowed
     */
    default TraversalControl backRef(TraverserContext<T> context) {
        return TraversalControl.CONTINUE;
    }

}
