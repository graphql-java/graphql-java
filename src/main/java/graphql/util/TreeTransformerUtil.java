package graphql.util;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Queue;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

@PublicApi
@NullMarked
public class TreeTransformerUtil {

    /**
     * Can be called multiple times to change the current node of the context. The latest call wins
     *
     * @param context the context in play
     * @param changedNode the changed node
     * @param <T> for two
     *
     * @return traversal control
     */
    public static <T> TraversalControl changeNode(TraverserContext<T> context, T changedNode) {
        boolean changed = context.isChanged();
        NodeZipper<T> zipperWithChangedNode = ((NodeZipper<T>) assertNotNull(context.getVar(NodeZipper.class), "nodeZipper should not be null")).withNewNode(changedNode);
        List<NodeZipper<T>> zippers = assertNotNull(context.getSharedContextData(), "shared zippers should not be null");
        if (changed) {
            // this is potentially expensive
            replaceZipperForNode(zippers, assertNotNull(context.thisNode(), "thisNode should not be null"), changedNode);
            context.changeNode(changedNode);
        } else {
            zippers.add(zipperWithChangedNode);
            context.changeNode(changedNode);
        }
        return TraversalControl.CONTINUE;
    }

    private static <T> void replaceZipperForNode(List<NodeZipper<T>> zippers, T currentNode, T newNode) {
        int index = FpKit.findIndex(zippers, zipper -> zipper.getCurNode() == currentNode);
        assertTrue(index >= 0, "No current zipper found for provided node");
        NodeZipper<T> newZipper = zippers.get(index).withNewNode(newNode);
        zippers.set(index, newZipper);
    }

    public static <T> TraversalControl deleteNode(TraverserContext<T> context) {
        NodeZipper<T> deleteNodeZipper = ((NodeZipper<T>) assertNotNull(context.getVar(NodeZipper.class), "nodeZipper should not be null")).deleteNode();
        Queue<NodeZipper<T>> zippers = assertNotNull(context.getSharedContextData(), "shared zippers should not be null");
        zippers.add(deleteNodeZipper);
        context.deleteNode();
        return TraversalControl.CONTINUE;
    }

    public static <T> TraversalControl insertAfter(TraverserContext<T> context, T toInsertAfter) {
        NodeZipper<T> insertNodeZipper = ((NodeZipper<T>) assertNotNull(context.getVar(NodeZipper.class), "nodeZipper should not be null")).insertAfter(toInsertAfter);
        Queue<NodeZipper<T>> zippers = assertNotNull(context.getSharedContextData(), "shared zippers should not be null");
        zippers.add(insertNodeZipper);
        return TraversalControl.CONTINUE;
    }

    public static <T> TraversalControl insertBefore(TraverserContext<T> context, T toInsertBefore) {
        NodeZipper<T> insertNodeZipper = ((NodeZipper<T>) assertNotNull(context.getVar(NodeZipper.class), "nodeZipper should not be null")).insertBefore(toInsertBefore);
        Queue<NodeZipper<T>> zippers = assertNotNull(context.getSharedContextData(), "shared zippers should not be null");
        zippers.add(insertNodeZipper);
        return TraversalControl.CONTINUE;
    }


}
