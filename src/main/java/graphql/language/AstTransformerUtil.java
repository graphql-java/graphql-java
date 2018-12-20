package graphql.language;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

@PublicApi
public class AstTransformerUtil {

    /**
     * Helper method to be used inside a {@link NodeVisitor} to actually a change a node.
     * <p>
     * It generates a new {@link AstZipper} and replaces the current accumulated {@link AstMultiZipper} including
     * the new {@link AstZipper}.
     *
     * @param context     the context in play
     * @param changedNode the changed node
     *
     * @return traversal control to allow for a more fluent coding style
     */
    public static TraversalControl changeNode(TraverserContext<Node> context, Node changedNode) {
        AstZipper zipperWithChangedNode = context.getVar(AstZipper.class).withNewNode(changedNode);
        AstMultiZipper multiZipper = context.getCurrentAccumulate();
        context.setAccumulate(multiZipper.withNewZipper(zipperWithChangedNode));
        context.changeNode(changedNode);
        return TraversalControl.CONTINUE;
    }
}
