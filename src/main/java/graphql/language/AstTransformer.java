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

import static graphql.Assert.assertNotNull;

@PublicApi
public class AstTransformer {


    public Node transform(Node root, NodeVisitor nodeVisitor) {
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
                    breadCrumbsStack.push(new AstBreadcrumb(context.getParentContext().thisNode(), location));
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
        AstMultiZipper multiZipperResult = (AstMultiZipper) nodeTraverser.traverse(root, nodeTraverserVisitor).getAccumulatedResult();
        return multiZipperResult.toRootNode();
    }

    /**
     * Helper method to be used inside a {@link NodeVisitor} to actually a change a node.<p/>
     * It generates a new {@link AstZipper} and replaces the current accumulated {@link AstMultiZipper} including the new
     * {@link AstZipper}.
     *
     * @param context
     * @param changedNode
     */
    public static void changeNode(TraverserContext<Node> context, Node changedNode) {
        AstZipper zipperWithChangedNode = context.getVar(AstZipper.class).withNewNode(changedNode);
        AstMultiZipper multiZipper = context.getCurrentAccumulate();
        context.setAccumulate(multiZipper.withNewZipper(zipperWithChangedNode));
    }


}
