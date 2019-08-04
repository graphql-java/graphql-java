package graphql.util;


import graphql.Assert;
import graphql.PublicApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static graphql.Assert.assertNotNull;

@PublicApi
public class TreeTransformer<T> {

    private final NodeAdapter<T> nodeAdapter;

    public TreeTransformer(NodeAdapter<T> nodeAdapter) {
        this.nodeAdapter = nodeAdapter;
    }

    public T transform(T root, TraverserVisitor<T> traverserVisitor) {
        return transform(root, traverserVisitor, Collections.emptyMap());
    }

    public T transform(T root, TraverserVisitor<T> traverserVisitor, Map<Class<?>, Object> rootVars) {
        assertNotNull(root);


        TraverserVisitor<T> nodeTraverserVisitor = new TraverserVisitor<T>() {

            @Override
            public TraversalControl enter(TraverserContext<T> context) {
                NodeZipper<T> nodeZipper = new NodeZipper<>(context.thisNode(), context.getBreadcrumbs(), nodeAdapter);
                context.setVar(NodeZipper.class, nodeZipper);
                context.setVar(NodeAdapter.class, nodeAdapter);
                return traverserVisitor.enter(context);
            }

            @Override
            public TraversalControl leave(TraverserContext<T> context) {
                return traverserVisitor.leave(context);
            }
        };

        List<NodeZipper<T>> zippers = new LinkedList<>();
        Traverser<T> traverser = Traverser.depthFirstWithNamedChildren(nodeAdapter::getNamedChildren, zippers, null);
        traverser.rootVars(rootVars);
        traverser.traverse(root, nodeTraverserVisitor);

        NodeMultiZipper<T> multiZipper = NodeMultiZipper.newNodeMultiZipperTrusted(root, zippers, nodeAdapter);
        return multiZipper.toRootNode();
    }

    public T transformParallel(T root, TraverserVisitor<T> traverserVisitor) {
        return transformParallel(root, traverserVisitor, Collections.emptyMap());

    }

    public T transformParallel(T root, TraverserVisitor<T> traverserVisitor, Map<Class<?>, Object> rootVars) {
        assertNotNull(root);


        TraverserVisitor<T> nodeTraverserVisitor = new TraverserVisitor<T>() {

            @Override
            public TraversalControl enter(TraverserContext<T> context) {
                NodeZipper<T> nodeZipper = new NodeZipper<>(context.thisNode(), context.getBreadcrumbs(), nodeAdapter);
                context.setVar(NodeZipper.class, nodeZipper);
                context.setVar(NodeAdapter.class, nodeAdapter);
                return traverserVisitor.enter(context);
            }

            @Override
            public TraversalControl leave(TraverserContext<T> context) {
                return Assert.assertShouldNeverHappen("not supposed to be called for parallel traversing");
            }
        };

        Queue<NodeZipper<T>> zippers = new ConcurrentLinkedQueue<>();
        ParallelTraverser<T> traverser = ParallelTraverser.parallelTraverserWithNamedChildren(nodeAdapter::getNamedChildren, zippers);
        traverser.rootVars(rootVars);
        traverser.traverse(root, nodeTraverserVisitor);

        NodeMultiZipper<T> multiZipper = NodeMultiZipper.newNodeMultiZipperTrusted(root, new ArrayList<>(zippers), nodeAdapter);
        return multiZipper.toRootNode();
    }
}
