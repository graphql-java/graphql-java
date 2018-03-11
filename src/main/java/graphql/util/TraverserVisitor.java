package graphql.util;

import graphql.Internal;

@Internal
public interface TraverserVisitor<T> {

    TraversalControl enter(TraverserContext<T> context);

    TraversalControl leave(TraverserContext<T> context);

    default TraversalControl backRef(TraverserContext<T> context) {
        return TraversalControl.CONTINUE;
    }

}
