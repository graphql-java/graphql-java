package graphql.language;

import graphql.PublicApi;
import graphql.execution2.result.Breadcrumb;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

@PublicApi
public class AstTransformer {


    public Node transform(Node root, NodeVisitor nodeVisitor) {

        AstMultiZipper astMultiZipper = new AstMultiZipper(root, Collections.emptyList());

        Deque<Breadcrumb> breadCrumbsStack = new ArrayDeque<>();
        TraverserVisitor<Node> nodeTraverserVisitor = new TraverserVisitor<Node>() {

            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
//                breadCrumbsStack.push(new AstBreadcrumb(context.thisNode(), context.);
                context.setVar(NodeTraverser.LeaveOrEnter.class, NodeTraverser.LeaveOrEnter.ENTER);
                return context.thisNode().accept(context, nodeVisitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                breadCrumbsStack.pop();
                return TraversalControl.CONTINUE;
            }

        };

        Traverser<Node> nodeTraverser = Traverser.depthFirst(Node::getChildren, null, astMultiZipper);
        AstMultiZipper multiZipperResult = (AstMultiZipper) nodeTraverser.traverse(root, nodeTraverserVisitor).getAccumulatedResult();
        return multiZipperResult.toRootNode();
    }


}
