package graphql.util;

import graphql.PublicApi;

import java.util.List;
import java.util.Queue;

import static graphql.Assert.assertTrue;

@PublicApi
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
        if (context.isParallel()) {
            List<NodeZipper<T>> zippers = context.getVar(List.class);
            NodeAdapter adapter = context.getVar(NodeAdapter.class);
            if (changed) {
                replaceZipperForNode(zippers, context.thisNode(), changedNode);
                context.changeNode(changedNode);
            } else {
                NodeZipper<T> nodeZipper = new NodeZipper<>(changedNode, context.getBreadcrumbs(), adapter);
                zippers.add(nodeZipper);
                context.changeNode(changedNode);
            }
            return TraversalControl.CONTINUE;
        } else {
            NodeZipper<T> zipperWithChangedNode = context.getVar(NodeZipper.class).withNewNode(changedNode);
            List<NodeZipper<T>> zippers = context.getSharedContextData();
            if (changed) {
                // this is potentially expensive
                replaceZipperForNode(zippers, context.thisNode(), changedNode);
                context.changeNode(changedNode);
            } else {
                zippers.add(zipperWithChangedNode);
                context.changeNode(changedNode);
            }
            return TraversalControl.CONTINUE;
        }
    }

    private static <T> void replaceZipperForNode(List<NodeZipper<T>> zippers, T currentNode, T newNode) {
        int index = FpKit.findIndex(zippers, zipper -> zipper.getCurNode() == currentNode);
        assertTrue(index >= 0, () -> "No current zipper found for provided node");
        NodeZipper<T> newZipper = zippers.get(index).withNewNode(newNode);
        zippers.set(index, newZipper);
    }

    public static <T> TraversalControl deleteNode(TraverserContext<T> context) {
        if (context.isParallel()) {
            NodeAdapter adapter = context.getVar(NodeAdapter.class);
            NodeZipper<T> deleteNodeZipper = new NodeZipper<>(context.thisNode(), context.getBreadcrumbs(), adapter).deleteNode();
            List<NodeZipper<T>> zippers = context.getVar(List.class);
            zippers.add(deleteNodeZipper);
            context.deleteNode();
            return TraversalControl.CONTINUE;
        } else {
            NodeZipper<T> deleteNodeZipper = context.getVar(NodeZipper.class).deleteNode();
            Queue<NodeZipper<T>> zippers = context.getSharedContextData();
            zippers.add(deleteNodeZipper);
            context.deleteNode();
            return TraversalControl.CONTINUE;
        }
    }

    public static <T> TraversalControl insertAfter(TraverserContext<T> context, T toInsertAfter) {
        if (context.isParallel()) {
            NodeAdapter adapter = context.getVar(NodeAdapter.class);
            NodeZipper<T> insertNodeZipper = new NodeZipper<>(context.originalThisNode(), context.getBreadcrumbs(), adapter).insertAfter(toInsertAfter);
            List<NodeZipper<T>> zippers = context.getVar(List.class);
            zippers.add(insertNodeZipper);
            return TraversalControl.CONTINUE;
        } else {
            NodeZipper<T> insertNodeZipper = context.getVar(NodeZipper.class).insertAfter(toInsertAfter);
            Queue<NodeZipper<T>> zippers = context.getSharedContextData();
            zippers.add(insertNodeZipper);
            return TraversalControl.CONTINUE;
        }
    }

    public static <T> TraversalControl insertBefore(TraverserContext<T> context, T toInsertBefore) {
        if (context.isParallel()) {
            NodeAdapter adapter = context.getVar(NodeAdapter.class);
            NodeZipper<T> insertNodeZipper = new NodeZipper<>(context.originalThisNode(), context.getBreadcrumbs(), adapter).insertBefore(toInsertBefore);
            List<NodeZipper<T>> zippers = context.getVar(List.class);
            zippers.add(insertNodeZipper);
            return TraversalControl.CONTINUE;
        } else {
            NodeZipper<T> insertNodeZipper = context.getVar(NodeZipper.class).insertBefore(toInsertBefore);
            Queue<NodeZipper<T>> zippers = context.getSharedContextData();
            zippers.add(insertNodeZipper);
            return TraversalControl.CONTINUE;
        }
    }


}
