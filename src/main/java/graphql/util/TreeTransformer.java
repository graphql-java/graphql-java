package graphql.util;


import graphql.PublicApi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static graphql.Assert.assertNotNull;

@PublicApi
public class TreeTransformer<T> {

    private final NodeAdapter<T> nodeAdapter;

    public TreeTransformer(NodeAdapter<T> nodeAdapter) {
        this.nodeAdapter = nodeAdapter;
    }

    public T transform(T root, TraverserVisitor<T> traverserVisitor) {
        assertNotNull(root);

        NodeMultiZipper<T> astMultiZipper = new NodeMultiZipper<>(root, Collections.emptyList(), nodeAdapter);

        Deque<Breadcrumb<T>> breadCrumbsStack = new ArrayDeque<>();
        TraverserVisitor<T> nodeTraverserVisitor = new TraverserVisitor<T>() {

            @Override
            public TraversalControl enter(TraverserContext<T> context) {
                NodeLocation generalNodeLocation = context.getPosition();
                if (generalNodeLocation != null) {
                    NodeLocation location = new NodeLocation(generalNodeLocation.getName(), generalNodeLocation.getIndex());
                    breadCrumbsStack.push(new Breadcrumb<>(context.getParentNode(), location));
                }
                List<Breadcrumb<T>> breadcrumbs = new ArrayList<>(breadCrumbsStack);
                NodeZipper<T> nodeZipper = new NodeZipper<>(context.thisNode(), breadcrumbs, nodeAdapter);
                context.setVar(NodeZipper.class, nodeZipper);
                context.setVar(NodeAdapter.class, nodeAdapter);
                return traverserVisitor.enter(context);
            }

            @Override
            public TraversalControl leave(TraverserContext<T> context) {
                if (!breadCrumbsStack.isEmpty()) {
                    breadCrumbsStack.pop();
                }
                return traverserVisitor.leave(context);
            }
        };

        Traverser<T> nodeTraverser = Traverser.depthFirstWithNamedChildren(nodeAdapter::getNamedChildren, null, astMultiZipper);
        NodeMultiZipper<T> multiZipperResult = (NodeMultiZipper<T>) nodeTraverser.traverse(root, nodeTraverserVisitor).getAccumulatedResult();
        return multiZipperResult.toRootNode();
    }
}
