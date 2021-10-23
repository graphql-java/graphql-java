package graphql.language;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import graphql.util.TraverserVisitorStub;
import graphql.util.TreeParallelTransformer;
import graphql.util.TreeTransformer;

import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import static graphql.Assert.assertNotNull;
import static graphql.language.AstNodeAdapter.AST_NODE_ADAPTER;

/**
 * Allows for an easy way to "manipulate" the immutable Ast by changing specific nodes and getting back a new Ast
 * containing the changed nodes while everything else is the same.
 */
@PublicApi
public class AstTransformer {


    public Node transform(Node root, NodeVisitor nodeVisitor) {
        assertNotNull(root);
        assertNotNull(nodeVisitor);

        TraverserVisitor<Node> traverserVisitor = getNodeTraverserVisitor(nodeVisitor);
        TreeTransformer<Node> treeTransformer = new TreeTransformer<>(AST_NODE_ADAPTER);
        return treeTransformer.transform(root, traverserVisitor);
    }

    public Node transform(Node root, NodeVisitor nodeVisitor, Map<Class<?>, Object> rootVars) {
        assertNotNull(root);
        assertNotNull(nodeVisitor);

        TraverserVisitor<Node> traverserVisitor = getNodeTraverserVisitor(nodeVisitor);
        TreeTransformer<Node> treeTransformer = new TreeTransformer<>(AST_NODE_ADAPTER);
        return treeTransformer.transform(root, traverserVisitor, rootVars);
    }

    public Node transformParallel(Node root, NodeVisitor nodeVisitor) {
        return transformParallel(root, nodeVisitor, ForkJoinPool.commonPool());
    }

    public Node transformParallel(Node root, NodeVisitor nodeVisitor, ForkJoinPool forkJoinPool) {
        assertNotNull(root);
        assertNotNull(nodeVisitor);

        TraverserVisitor<Node> traverserVisitor = new TraverserVisitorStub<Node>() {
            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                return context.thisNode().accept(context, nodeVisitor);
            }

        };

        TreeParallelTransformer<Node> treeParallelTransformer = TreeParallelTransformer.parallelTransformer(AST_NODE_ADAPTER, forkJoinPool);
        return treeParallelTransformer.transform(root, traverserVisitor);
    }

    private TraverserVisitor<Node> getNodeTraverserVisitor(NodeVisitor nodeVisitor) {


        return new TraverserVisitor<Node>() {
            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                return context.thisNode().accept(context, nodeVisitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                return TraversalControl.CONTINUE;
            }
        };
    }
}
