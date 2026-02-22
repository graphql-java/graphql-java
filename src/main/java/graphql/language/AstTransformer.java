package graphql.language;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;
import graphql.util.TraverserVisitorStub;
import graphql.util.TreeParallelTransformer;
import graphql.util.TreeTransformer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import static graphql.Assert.assertNotNull;
import static graphql.language.AstNodeAdapter.AST_NODE_ADAPTER;

/**
 * Allows for an easy way to "manipulate" the immutable Ast by changing specific nodes and getting back a new Ast
 * containing the changed nodes while everything else is the same.
 */
@PublicApi
@NullMarked
public class AstTransformer {

    /**
     * Transforms the input tree using the Visitor Pattern.
     * @param root the root node of the input tree.
     * @param nodeVisitor the visitor which will transform the input tree.
     * @return the transformed tree.
     */
    public Node transform(Node root, NodeVisitor nodeVisitor) {
        assertNotNull(root);
        assertNotNull(nodeVisitor);

        TraverserVisitor<Node> traverserVisitor = getNodeTraverserVisitor(nodeVisitor);
        TreeTransformer<Node> treeTransformer = new TreeTransformer<>(AST_NODE_ADAPTER);
        return treeTransformer.transform(root, traverserVisitor);
    }

    /**
     * Transforms the input tree using the Visitor Pattern.
     * @param root the root node of the input tree.
     * @param nodeVisitor the visitor which will transform the input tree.
     * @param rootVars a context argument to pass information into the nodeVisitor. Pass a contextual
     *                 object to your visitor by adding it to this map such that such that the key
     *                 is the class of the object, and the value is the object itself. The object
     *                 can be retrieved within the visitor by calling context.getVarFromParents().
     * @return the transformed tree.
     */
    public Node transform(Node root, NodeVisitor nodeVisitor, @Nullable Map<Class<?>, Object> rootVars) {
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
