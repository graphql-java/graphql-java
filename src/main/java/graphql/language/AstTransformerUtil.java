package graphql.language;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static graphql.Assert.assertTrue;

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

    public static TraversalControl deleteNode(TraverserContext<Node> context) {
        assertTrue(context.getParentNode() != null, "can't delete root node");
        AstMultiZipper multiZipper = context.getCurrentAccumulate();
        AstZipper curZipper = context.getVar(AstZipper.class);
        AstZipper zipperForParent = multiZipper.getZipperForNode(context.getParentContext().thisNode());
        boolean zipperForParentAlreadyExisted = true;
        if (zipperForParent == null) {
            zipperForParent = curZipper.moveUp();
            zipperForParentAlreadyExisted = false;
        }

        Node parentNode = zipperForParent.getCurNode();
        NodeLocation nodeLocation = curZipper.getBreadcrumbs().get(0).getLocation();
        Node newParent = NodeUtil.removeChild(parentNode, nodeLocation);
        AstZipper newZipperForParent = zipperForParent.withNewNode(newParent);

        AstMultiZipper newMultiZipper;
        if (zipperForParentAlreadyExisted) {
            newMultiZipper = multiZipper.withReplacedZipper(zipperForParent, newZipperForParent);
        } else {
            newMultiZipper = multiZipper.withNewZipper(newZipperForParent);
        }

        context.setAccumulate(newMultiZipper);
        context.deleteNode();
        return TraversalControl.CONTINUE;
    }


}
