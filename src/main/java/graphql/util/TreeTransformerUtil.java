package graphql.util;

import graphql.PublicApi;

import java.util.function.Function;

import static graphql.Assert.assertTrue;

@PublicApi
public class TreeTransformerUtil {

    public static <T> TraversalControl changeNode(TraverserContext<T> context, T changedNode) {
        NodeZipper<T> zipperWithChangedNode = context.getVar(NodeZipper.class).withNewNode(changedNode);
        NodeMultiZipper<T> multiZipper = context.getCurrentAccumulate();
        context.setAccumulate(multiZipper.withNewZipper(zipperWithChangedNode));
        context.changeNode(changedNode);
        return TraversalControl.CONTINUE;
    }

    public static <T> TraversalControl deleteNode(TraverserContext<T> context) {
        NodeZipper<T> curZipper = context.getVar(NodeZipper.class);
        NodeAdapter<T> nodeAdaper = context.getVar(NodeAdapter.class);
        NodeLocation nodeLocation = curZipper.getBreadcrumbs().get(0).getLocation();

        changeParentNode(context, parentNode -> nodeAdaper.removeChild(parentNode, nodeLocation));

        context.deleteNode();
        return TraversalControl.CONTINUE;
    }

    public static <T> TraversalControl changeParentNode(TraverserContext<T> context, Function<T, T> changeNodeFunction) {
        assertTrue(context.getParentNode() != null, "can't delete root node");
        NodeMultiZipper<T> multiZipper = context.getCurrentAccumulate();
        NodeZipper<T> curZipper = context.getVar(NodeZipper.class);
        NodeZipper<T> zipperForParent = multiZipper.getZipperForNode(curZipper.getParent());

        boolean zipperForParentAlreadyExisted = true;
        if (zipperForParent == null) {
            zipperForParent = curZipper.moveUp();
            zipperForParentAlreadyExisted = false;
        }
        T parentNode = zipperForParent.getCurNode();

        T newParent = changeNodeFunction.apply(parentNode);

        NodeZipper newZipperForParent = zipperForParent.withNewNode(newParent);

        NodeMultiZipper<T> newMultiZipper;
        if (zipperForParentAlreadyExisted) {
            newMultiZipper = multiZipper.withReplacedZipper(zipperForParent, newZipperForParent);
        } else {
            newMultiZipper = multiZipper.withNewZipper(newZipperForParent);
        }
        context.getParentContext().changeNode(newParent);
        context.setAccumulate(newMultiZipper);
        return TraversalControl.CONTINUE;

    }
}
