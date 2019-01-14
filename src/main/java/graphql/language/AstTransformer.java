package graphql.language;

import graphql.PublicApi;
import graphql.util.NodePosition;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

/**
 * Allows for an easy way to "manipulate" the immutable Ast by changing specific nodes and getting back a new Ast
 * containing the changed nodes while everything else is the same.
 */
@PublicApi
public class AstTransformer {
    public Node transform(Node root, NodeVisitor nodeVisitor, Map<Class<?>, Object> rootVars) {
        assertNotNull(root);
        assertNotNull(nodeVisitor);

        AstMultiZipper astMultiZipper = new AstMultiZipper(root, Collections.emptyList());

        Deque<AstBreadcrumb> breadCrumbsStack = new ArrayDeque<>();
        TraverserVisitor<Node> nodeTraverserVisitor = new TraverserVisitor<Node>() {

            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                NodePosition generalNodePosition = context.getPosition();
                if (generalNodePosition != null) {
                    NodeLocation location = new NodeLocation(generalNodePosition.getName(), generalNodePosition.getIndex());
                    breadCrumbsStack.push(new AstBreadcrumb(context.getParentNode(), location));
                }
                List<AstBreadcrumb> breadcrumbs = new ArrayList<>(breadCrumbsStack);
                AstZipper astZipper = new AstZipper(context.thisNode(), breadcrumbs);
                context.setVar(AstZipper.class, astZipper);
                context.setVar(NodeTraverser.LeaveOrEnter.class, NodeTraverser.LeaveOrEnter.ENTER);
                return context.thisNode().accept(context, nodeVisitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                if (!breadCrumbsStack.isEmpty()) {
                    breadCrumbsStack.pop();
                }
                return TraversalControl.CONTINUE;
            }

        };

        Traverser<Node> nodeTraverser = Traverser.depthFirstWithNamedChildren(node -> node.getNamedChildren().getChildren(), null, astMultiZipper);
        nodeTraverser.rootVars(rootVars);
        AstMultiZipper multiZipperResult = (AstMultiZipper) nodeTraverser.traverse(root, nodeTraverserVisitor).getAccumulatedResult();
        return multiZipperResult.toRootNode();
    }

    public Node transform(Node root, NodeVisitor nodeVisitor) {
        return transform(root, nodeVisitor, Collections.emptyMap());
    }
}
