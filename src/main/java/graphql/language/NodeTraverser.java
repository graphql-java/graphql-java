package graphql.language;

import graphql.PublicApi;
import graphql.util.DefaultTraverserContext;
import graphql.util.TraversalControl;
import graphql.util.Traverser;
import static graphql.util.Traverser.newTraverser;
import graphql.util.TraverserState;
import graphql.util.TraverserVisitor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import graphql.util.TraverserContext;
import graphql.util.TraverserState.QueueTraverserState;
import graphql.util.TraverserState.StackTraverserState;

/**
 * Lets you traverse a {@link Node} tree.
 */
@PublicApi
public class NodeTraverser {


    /**
     * Used to indicate via {@link TraverserContext#getVar(Class)} if the visit happens inside the ENTER or LEAVE phase.
     */
    public enum LeaveOrEnter {
        LEAVE,
        ENTER
    }

    private final Map<Class<?>, Object> rootVars;
    private final Function<? super Node, ? extends List<Node>> getChildren;
    private final Object initialData;

    public NodeTraverser(Map<Class<?>, Object> rootVars, Function<? super Node, ? extends List<Node>> getChildren, Object initialData) {
        this.rootVars = rootVars;
        this.getChildren = getChildren;
        this.initialData = initialData;
    }

    public NodeTraverser(Map<Class<?>, Object> rootVars, Function<? super Node, ? extends List<Node>> getChildren) {
        this(rootVars, getChildren, null);
    }

    public NodeTraverser(Function<? super Node, ? extends List<Node>> getChildren, Object initialData) {
        this(Collections.emptyMap(), getChildren, initialData);
    }
    
    public NodeTraverser(Object initialData) {
        this(Collections.emptyMap(), Node::getChildren, initialData);
    }
    
    public NodeTraverser() {
        this(null);
    }

    /**
     * depthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param root        the root node
     *
     * @return the accumulation result of this traversal
     */
    public Object depthFirst(NodeVisitor nodeVisitor, Node root) {
        return depthFirst(nodeVisitor, Collections.singleton(root));
    }

    /**
     * depthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param root        the root node
     * @param stateCreator    factory to create TraverserState instance
     *
     * @return the accumulation result of this traversal
     */
    public Object depthFirst(NodeVisitor nodeVisitor, Node root, Function<? super Object, ? extends StackTraverserState<Node>> stateCreator) {
        return depthFirst(nodeVisitor, Collections.singleton(root), stateCreator);
    }

    /**
     * depthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     *
     * @return the accumulation result of this traversal
     */
    public Object depthFirst(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {
        return depthFirst(nodeVisitor, roots, TraverserState::newStackState);
    }

    /**
     * depthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     * @param stateCreator    factory to create TraverserState instance
     *
     * @return the accumulation result of this traversal
     */
    public Object depthFirst(NodeVisitor nodeVisitor, Collection<? extends Node> roots, Function<? super Object, ? extends StackTraverserState<Node>> stateCreator) {
        return doTraverse(roots, decorate(nodeVisitor), stateCreator);
    }
    
    /**
     * breadthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param root        the root node
     */
    public void breadthFirst(NodeVisitor nodeVisitor, Node root) {
        breadthFirst(nodeVisitor, Collections.singleton(root));
    }
        
    /**
     * breadthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param root        the root node
     * @param stateCreator    factory to create TraverserState instance
     */
    public void breadthFirst(NodeVisitor nodeVisitor, Node root, Function<? super Object, ? extends QueueTraverserState<Node>> stateCreator) {
        breadthFirst(nodeVisitor, Collections.singleton(root));
    }
    
    /**
     * breadthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     */
    public void breadthFirst(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {
        breadthFirst(nodeVisitor, roots, TraverserState::newQueueState);
    }

    /**
     * breadthFirst traversal with a enter/leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     * @param stateCreator    factory to create TraverserState instance
     */
    public void breadthFirst(NodeVisitor nodeVisitor, Collection<? extends Node> roots, Function<? super Object, ? extends QueueTraverserState<Node>> stateCreator) {
        doTraverse(roots, decorate(nodeVisitor), stateCreator);
    }
    
    private static TraverserVisitor<Node> decorate (NodeVisitor nodeVisitor) {
        return new TraverserVisitor<Node>() {
            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                context.setVar(LeaveOrEnter.class, LeaveOrEnter.ENTER);
                return context.thisNode().accept(context, nodeVisitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                context.setVar(LeaveOrEnter.class, LeaveOrEnter.LEAVE);
                return context.thisNode().accept(context, nodeVisitor);
            }
        };
    }
    
    /**
     * Version of {@link #preOrder(NodeVisitor, Collection)} with one root.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param root        the root node
     *
     * @return the accumulation result of this traversal
     */
    public Object preOrder(NodeVisitor nodeVisitor, Node root) {
        return preOrder(nodeVisitor, Collections.singleton(root));
    }

    /**
     * Pre-Order traversal: This is a specialized version of depthFirst with only the enter phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     *
     * @return the accumulation result of this traversal
     */
    public Object preOrder(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {
        return preOrder(nodeVisitor, roots, TraverserState::newStackState);
    }
    
    /**
     * Pre-Order traversal: This is a specialized version of depthFirst with only the enter phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     * @param stateCreator    TraverserState factory
     *
     * @return the accumulation result of this traversal
     */
    public Object preOrder(NodeVisitor nodeVisitor, Collection<? extends Node> roots, Function<? super Object, ? extends TraverserState<Node>> stateCreator) {
        TraverserVisitor<Node> nodeTraverserVisitor = new TraverserVisitor<Node>() {

            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                context.setVar(LeaveOrEnter.class, LeaveOrEnter.ENTER);
                return context.thisNode().accept(context, nodeVisitor);
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                return TraversalControl.CONTINUE;
            }

        };
        return doTraverse(roots, nodeTraverserVisitor);
        
    }

    /**
     * Version of {@link #postOrder(NodeVisitor, Collection)} with one root.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param root        the root node
     *
     * @return the accumulation result of this traversal
     */
    public Object postOrder(NodeVisitor nodeVisitor, Node root) {
        return postOrder(nodeVisitor, Collections.singleton(root));
    }
    
    /**
     * Post-Order traversal: This is a specialized version of depthFirst with only the leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     *
     * @return the accumulation result of this traversal
     */
    public Object postOrder(NodeVisitor nodeVisitor, Collection<? extends Node> roots) {
        return postOrder(nodeVisitor, roots, TraverserState::newStackState);
    }

    /**
     * Post-Order traversal: This is a specialized version of depthFirst with only the leave phase.
     *
     * @param nodeVisitor the visitor of the nodes
     * @param roots       the root nodes
     * @param stateCreator    TraverserState factory
     *
     * @return the accumulation result of this traversal
     */
    public Object postOrder(NodeVisitor nodeVisitor, Collection<? extends Node> roots, Function<? super Object, ? extends TraverserState<Node>> stateCreator) {
        TraverserVisitor<Node> nodeTraverserVisitor = new TraverserVisitor<Node>() {

            @Override
            public TraversalControl enter(TraverserContext<Node> context) {
                return TraversalControl.CONTINUE;
            }

            @Override
            public TraversalControl leave(TraverserContext<Node> context) {
                context.setVar(LeaveOrEnter.class, LeaveOrEnter.LEAVE);
                return context.thisNode().accept(context, nodeVisitor);
            }

        };
        return doTraverse(roots, nodeTraverserVisitor, stateCreator);
    }

    private Object doTraverse(Collection<? extends Node> roots, TraverserVisitor<Node> traverserVisitor) {
        return doTraverse(roots, traverserVisitor, TraverserState::newStackState);
    }
    
    private Object doTraverse(Collection<? extends Node> roots, TraverserVisitor<Node> traverserVisitor, Function<? super Object, ? extends TraverserState<Node>> stateCreator) {
        Traverser<Node> nodeTraverser = newTraverser(stateCreator.apply(initialData), getChildren, null);
        nodeTraverser.rootVars(rootVars);
        return nodeTraverser.traverse(roots, traverserVisitor).getAccumulatedResult();
    }

    @SuppressWarnings("TypeParameterUnusedInFormals")
    public static <T> T oneVisitWithResult(Node node, NodeVisitor nodeVisitor) {
        DefaultTraverserContext<Node> context = DefaultTraverserContext.simple(node);
        node.accept(context, nodeVisitor);
        return (T) context.getNewAccumulate();
    }

}
