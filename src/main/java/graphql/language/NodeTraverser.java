package graphql.language;

import graphql.util.SimpleTraverserContext;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import graphql.util.TraverserContext;
import graphql.util.TraverserVisitor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NodeTraverser {

    public enum LeaveOrEnter {
        LEAVE,
        ENTER
    }

    private final Map<Class<?>, Object> rootVars;
    private final Function<? super Node, ? extends List<Node>> getChildren;

    public NodeTraverser(Map<Class<?>, Object> rootVars, Function<? super Node, ? extends List<Node>> getChildren) {
        this.rootVars = rootVars;
        this.getChildren = getChildren;
    }

    public NodeTraverser() {
        this(Collections.emptyMap(), Node::getChildren);
    }


    public void depthFirst(NodeVisitor nodeVisitor, Node root) {
        doTraverse(nodeVisitor, Collections.singleton(root));
    }

    public void depthFirst(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {
        doTraverse(nodeVisitor, roots);
    }

    private void doTraverse(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {
        Traverser<Node> nodeTraverser = Traverser.depthFirst(this.getChildren);
        nodeTraverser.rootVars(rootVars);
        TraverserVisitor<Node> traverserVisitor = new TraverserVisitor<Node>() {

            public TraversalControl enter(TraverserContext<Node> context) {
                context.setVar(LeaveOrEnter.class, LeaveOrEnter.ENTER);
                return context.thisNode().accept(context, nodeVisitor);
            }

            public TraversalControl leave(TraverserContext<Node> context) {
                context.setVar(LeaveOrEnter.class, LeaveOrEnter.LEAVE);
                return context.thisNode().accept(context, nodeVisitor);
            }
        };
        nodeTraverser.traverse(roots, traverserVisitor);
    }

    public static <T> T oneVisitWithResult(Node node, NodeVisitor nodeVisitor) {
        SimpleTraverserContext<Node> context = new SimpleTraverserContext<>(node);
        node.accept(context, nodeVisitor);
        return (T) context.getResult();
    }

}
